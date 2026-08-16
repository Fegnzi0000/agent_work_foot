package com.hyf.agent_work_foot.slot;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.config.SlotProperties;
import com.hyf.agent_work_foot.diet.DietResponses;
import com.hyf.agent_work_foot.diet.DietService;
import com.hyf.agent_work_foot.food.FoodPriceParser;
import com.hyf.agent_work_foot.food.FoodQueryService;
import com.hyf.agent_work_foot.slot.entity.SlotSpinEntity;
import com.hyf.agent_work_foot.slot.mapper.SlotSpinMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Slot应用服务，编排完整食物池随机、结果快照、生命周期与确认幂等事务。 */
@Service
public class SlotService {
    private static final Logger LOG = LoggerFactory.getLogger(SlotService.class);
    private static final String GENERATED = "GENERATED";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String EXPIRED = "EXPIRED";
    private final SlotSpinMapper mapper;
    private final FoodQueryService foodQueryService;
    private final DietService dietService;
    private final SlotRandomSelector selector;
    private final SlotRateLimiter rateLimiter;
    private final SlotProperties properties;
    private final FoodPriceParser priceParser;
    private final Clock clock;

    /** 作用：注入Slot全部模块边界。输入：Mapper、Food/Diet服务、随机、限流、配置、金额格式器和时钟。输出：服务实例。 */
    public SlotService(SlotSpinMapper mapper, FoodQueryService foodQueryService, DietService dietService,
                       SlotRandomSelector selector, SlotRateLimiter rateLimiter, SlotProperties properties,
                       FoodPriceParser priceParser, Clock clock) {
        this.mapper = mapper;
        this.foodQueryService = foodQueryService;
        this.dietService = dietService;
        this.selector = selector;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.priceParser = priceParser;
        this.clock = clock;
    }

    /**
     * 作用：生成一次独立Spin。
     * 输入：认证用户和可空previousSpinId。输出：选中食物快照与有效期。
     * 逻辑：验证真实上一结果、仅本次排除，然后从完整有效池等概率选取并保存快照。
     */
    @Transactional(noRollbackFor = ApiException.class)
    public SlotResponses.SpinData spin(String userId, String previousSpinId) {
        rateLimiter.check(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        String excludedFoodId = previousSpinId == null ? null : validatePrevious(userId, previousSpinId, now);
        List<FoodQueryService.FoodSnapshot> candidates = foodQueryService.findAllActiveSnapshots(userId);
        if (candidates.isEmpty()) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "FOOD_POOL_EMPTY", "食物池为空，请先添加食物");
        if (excludedFoodId != null) candidates = candidates.stream().filter(food -> !food.foodOptionId().equals(excludedFoodId)).toList();
        if (candidates.isEmpty()) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "SLOT_RETRY_UNAVAILABLE", "当前食物池无法重转");
        FoodQueryService.FoodSnapshot selected = selector.select(candidates);
        SlotSpinEntity entity = entity(userId, selected, now);
        mapper.insert(entity);
        LOG.info("slot generated userId={} spinId={} status={}", userId, entity.getId(), GENERATED);
        return spinResponse(entity);
    }

    /**
     * 作用：确认Spin并幂等创建Diet记录。
     * 输入：用户、Spin ID和确认表单。输出：首次或既有Diet响应。
     * 逻辑：行锁下先处理已确认，再处理过期，最后原子创建记录并更新确认关联。
     */
    @Transactional(noRollbackFor = ApiException.class)
    public DietResponses.DietRecordData confirm(String userId, String spinId, SlotRequests.ConfirmRequest request) {
        SlotSpinEntity spin = mapper.selectOwnedForUpdate(userId, spinId);
        if (spin == null) throw notFound();
        if (CONFIRMED.equals(spin.getStatus()) || spin.getConfirmedDietRecordId() != null) return confirmedRecord(userId, spin);
        LocalDateTime now = LocalDateTime.now(clock);
        if (EXPIRED.equals(spin.getStatus()) || !now.isBefore(spin.getExpiresAt())) {
            if (GENERATED.equals(spin.getStatus())) mapper.markExpired(userId, spinId, now);
            LOG.info("slot expired userId={} spinId={} status={}", userId, spinId, EXPIRED);
            throw error(HttpStatus.UNPROCESSABLE_CONTENT, "SLOT_SPIN_EXPIRED", "老虎机结果已失效");
        }
        if (!GENERATED.equals(spin.getStatus())) throw error(HttpStatus.CONFLICT, "SLOT_STATE_CONFLICT", "老虎机结果状态冲突");
        if (request == null) throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "确认请求不能为空");
        DietResponses.DietRecordData diet = dietService.createFromSlot(userId, spinId,
                new DietService.SlotFoodInput(spin.getSelectedFoodOptionId(), spin.getSelectedNameSnapshot(),
                        spin.getSelectedCategorySnapshot(), spin.getSelectedTagsSnapshotJson()),
                request.actualPrice(), request.mealType(), request.eatenAt());
        if (mapper.markConfirmed(userId, spinId, diet.id(), now) != 1) {
            throw error(HttpStatus.CONFLICT, "SLOT_STATE_CONFLICT", "老虎机结果状态冲突");
        }
        LOG.info("slot confirmed userId={} spinId={} status={}", userId, spinId, CONFIRMED);
        return diet;
    }

    /** 作用：校验重转依据并返回需排除食物。输入：用户、上一Spin与当前UTC时间。输出：食物ID。逻辑：归属、确认和过期状态均由服务端判断。 */
    private String validatePrevious(String userId, String previousSpinId, LocalDateTime now) {
        SlotSpinEntity previous = mapper.selectOwnedForUpdate(userId, previousSpinId);
        if (previous == null) throw notFound();
        if (CONFIRMED.equals(previous.getStatus()) || previous.getConfirmedDietRecordId() != null) {
            throw error(HttpStatus.CONFLICT, "SLOT_SPIN_ALREADY_CONFIRMED", "已确认结果不能用于重转");
        }
        if (EXPIRED.equals(previous.getStatus()) || !now.isBefore(previous.getExpiresAt())) {
            if (GENERATED.equals(previous.getStatus())) mapper.markExpired(userId, previousSpinId, now);
            throw error(HttpStatus.UNPROCESSABLE_CONTENT, "SLOT_SPIN_EXPIRED", "老虎机结果已失效");
        }
        if (!GENERATED.equals(previous.getStatus())) throw error(HttpStatus.CONFLICT, "SLOT_STATE_CONFLICT", "老虎机结果状态冲突");
        return previous.getSelectedFoodOptionId();
    }

    /** 作用：返回已确认记录。输入：用户和锁定Spin。输出：原Diet响应。逻辑：删除后返回冲突，绝不重新创建。 */
    private DietResponses.DietRecordData confirmedRecord(String userId, SlotSpinEntity spin) {
        if (spin.getConfirmedDietRecordId() == null) throw error(HttpStatus.CONFLICT, "SLOT_STATE_CONFLICT", "确认记录关联缺失");
        DietService.SlotRecordLookup lookup = dietService.findSlotRecord(userId, spin.getConfirmedDietRecordId());
        if (lookup == null) throw error(HttpStatus.CONFLICT, "SLOT_STATE_CONFLICT", "确认记录不存在");
        if (lookup.deleted()) throw error(HttpStatus.CONFLICT, "SLOT_ALREADY_CONFIRMED_RECORD_DELETED", "已确认的饮食记录已删除");
        return lookup.data();
    }

    /** 作用：构造待保存Spin。输入：用户、选中快照和当前时间。输出：完整实体。逻辑：UUID由应用生成，有效期来自配置。 */
    private SlotSpinEntity entity(String userId, FoodQueryService.FoodSnapshot selected, LocalDateTime now) {
        SlotSpinEntity entity = new SlotSpinEntity(); entity.setId(UUID.randomUUID().toString()); entity.setUserId(userId);
        entity.setSelectedFoodOptionId(selected.foodOptionId()); entity.setSelectedNameSnapshot(selected.name());
        entity.setSelectedCategorySnapshot(selected.category()); entity.setSelectedPriceSnapshot(selected.defaultPrice());
        entity.setSelectedTagsSnapshotJson(List.copyOf(selected.tags())); entity.setStatus(GENERATED);
        entity.setExpiresAt(now.plus(properties.spinTtl())); return entity;
    }

    /** 作用：转换公开Spin响应。输入：已保存实体。输出：不含状态和概率的数据。逻辑：金额两位、时间UTC Z格式。 */
    private SlotResponses.SpinData spinResponse(SlotSpinEntity entity) {
        return new SlotResponses.SpinData(entity.getId(), new SlotResponses.SelectedFood(entity.getSelectedFoodOptionId(),
                entity.getSelectedNameSnapshot(), entity.getSelectedCategorySnapshot(),
                priceParser.format(entity.getSelectedPriceSnapshot()), List.copyOf(entity.getSelectedTagsSnapshotJson())),
                entity.getExpiresAt().atOffset(ZoneOffset.UTC).toString());
    }

    /** 作用：构造统一的资源隐藏异常。输入：无。输出：404业务异常。逻辑：不存在与跨用户使用相同响应。 */
    private ApiException notFound() {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在或不属于当前用户");
    }

    /** 作用：构造Slot业务异常。输入：HTTP状态、错误码和消息。输出：统一ApiException。逻辑：交由全局异常处理器包装requestId。 */
    private ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}

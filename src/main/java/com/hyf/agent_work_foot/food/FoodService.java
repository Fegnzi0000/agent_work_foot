package com.hyf.agent_work_foot.food;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.FieldErrorDetail;
import com.hyf.agent_work_foot.common.PatchField;
import com.hyf.agent_work_foot.food.entity.FoodOptionEntity;
import com.hyf.agent_work_foot.food.entity.FoodOptionTagEntity;
import com.hyf.agent_work_foot.food.mapper.FoodOptionMapper;
import com.hyf.agent_work_foot.food.mapper.FoodOptionTagMapper;
import com.hyf.agent_work_foot.food.mapper.model.FoodPageRow;
import com.hyf.agent_work_foot.food.mapper.model.FoodTagRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 食物池应用服务，编排分页、CRUD、归属校验、规范化与事务。 */
@Service
public class FoodService {
    private final FoodOptionMapper foodMapper;
    private final FoodOptionTagMapper tagMapper;
    private final FoodNormalizer normalizer;
    private final FoodPriceParser priceParser;
    private final FoodContentValidator validator;
    private final Clock clock;

    /**
     * 作用：创建食物池应用服务。
     * 输入：主表/标签 Mapper、规范化器、金额解析器、内容校验器和 UTC 时钟。输出：服务实例。
     * 逻辑：保存模块依赖，所有公开用例统一经这些组件访问数据和规则。
     */
    public FoodService(FoodOptionMapper foodMapper, FoodOptionTagMapper tagMapper, FoodNormalizer normalizer,
                       FoodPriceParser priceParser, FoodContentValidator validator, Clock clock) {
        this.foodMapper = foodMapper;
        this.tagMapper = tagMapper;
        this.normalizer = normalizer;
        this.priceParser = priceParser;
        this.validator = validator;
        this.clock = clock;
    }

    /** 作用：分页查询当前用户食物池。输入：用户、0基分页和筛选。输出：标准分页响应。逻辑：复杂SQL分页后批量组装标签。 */
    public FoodResponses.FoodPageData list(String userId, int page, int size, String keyword,
                                           String category, List<String> tags) {
        validatePage(page, size, tags);
        String normalizedKeyword = normalizer.escapeLike(normalizer.normalizedName(keyword));
        String normalizedCategory = normalizer.escapeLike(normalizer.normalizedCategory(category));
        List<String> normalizedTags = normalizer.filterTags(tags);
        IPage<FoodPageRow> result = foodMapper.selectFoodPage(new Page<>(page + 1L, size), userId,
                normalizedKeyword, normalizedCategory, normalizedTags, normalizedTags.size());
        Map<String, List<String>> tagsByFood = tagsByFood(result.getRecords().stream().map(FoodPageRow::id).toList());
        List<FoodResponses.FoodOptionData> items = result.getRecords().stream()
                .map(row -> response(row.id(), row.name(), row.category(), row.defaultPrice(),
                        tagsByFood.getOrDefault(row.id(), List.of()), row.source())).toList();
        return new FoodResponses.FoodPageData(items, page, size, result.getTotal(), result.getPages());
    }

    /** 作用：创建自定义食物。输入：当前用户和完整请求。输出：数据库最终状态。逻辑：单事务插入主表和批量标签，重复统一映射409。 */
    @Transactional
    public FoodResponses.FoodOptionData create(String userId, FoodRequests.FoodWriteRequest request) {
        PreparedFood prepared = prepare(request.name(), request.category(), request.defaultPrice(), request.tags());
        ensureNotDuplicate(userId, prepared.activeKey(), null);
        FoodOptionEntity entity = entity(userId, prepared, FoodConstants.SOURCE_CUSTOM);
        try {
            foodMapper.insert(entity);
            insertTags(entity.getId(), prepared.tags());
        } catch (DuplicateKeyException exception) {
            throw duplicate();
        }
        return requiredResponse(userId, entity.getId());
    }

    /** 作用：读取当前用户有效食物详情。输入：用户与ID。输出：完整六字段响应。逻辑：不存在、已删或越权统一404。 */
    public FoodResponses.FoodOptionData get(String userId, String foodId) { return requiredResponse(userId, foodId); }

    /**
     * 作用：部分修改食物。输入：当前用户、ID和字段存在性请求。输出：最终完整数据。
     * 逻辑：锁定主表，合并字段，重新校验并在同一事务更新主表和可选标签。
     */
    @Transactional
    public FoodResponses.FoodOptionData patch(String userId, String foodId, FoodRequests.FoodPatchRequest request) {
        if (request.empty()) throw validation("body", "至少需要提供一个可修改字段");
        rejectNull(request.name(), "name"); rejectNull(request.category(), "category");
        rejectNull(request.defaultPrice(), "defaultPrice"); rejectNull(request.tags(), "tags");
        FoodOptionEntity current = foodMapper.selectOwnedActiveForUpdate(userId, foodId);
        if (current == null) throw notFound();
        List<String> currentTags = tagMapper.selectByFoodIds(List.of(foodId)).stream().map(FoodTagRow::tag).toList();
        String name = request.name().defined() ? request.name().value() : current.getName();
        String category = request.category().defined() ? request.category().value() : current.getCategory();
        String price = request.defaultPrice().defined() ? request.defaultPrice().value() : current.getDefaultPrice().toPlainString();
        List<String> tags = request.tags().defined() ? request.tags().value() : currentTags;
        PreparedFood prepared = prepare(name, category, price, tags);
        ensureNotDuplicate(userId, prepared.activeKey(), foodId);
        current.setName(prepared.name()); current.setNormalizedName(prepared.normalizedName());
        current.setCategory(prepared.category()); current.setDefaultPrice(prepared.price());
        current.setActiveUniqueKey(prepared.activeKey());
        try {
            current.setUpdatedAt(LocalDateTime.now(clock));
            if (foodMapper.updateOwnedActive(userId, current) == 0) {
                throw notFound();
            }
            if (request.tags().defined()) {
                tagMapper.deleteByFoodId(foodId);
                insertTags(foodId, prepared.tags());
            }
        } catch (DuplicateKeyException exception) { throw duplicate(); }
        return requiredResponse(userId, foodId);
    }

    /** 作用：软删除当前用户有效食物。输入：用户与ID。输出：无。逻辑：条件更新清空唯一键，0行统一404。 */
    @Transactional
    public void delete(String userId, String foodId) {
        if (foodMapper.softDelete(userId, foodId, LocalDateTime.now(clock)) == 0) throw notFound();
    }

    /**
     * 作用：为手工饮食记录解析或创建可关联食物。
     * 输入：用户、已提交的食物内容与本次实际价格。输出：有效食物 ID。
     * 逻辑：重复项直接关联且不更新；新项以CUSTOM来源和本次价格创建，调用者事务负责与饮食记录原子提交。
     */
    public String resolveOrCreateForManualRecord(String userId, String name, String category, List<String> tags,
                                                 BigDecimal actualPrice) {
        PreparedFood prepared = prepare(name, category, actualPrice.toPlainString(), tags);
        FoodOptionEntity existing = foodMapper.selectOwnedActive(userId, findDuplicateId(userId, prepared.activeKey()));
        if (existing != null) {
            return existing.getId();
        }
        FoodOptionEntity entity = entity(userId, prepared, FoodConstants.SOURCE_CUSTOM);
        try {
            foodMapper.insert(entity);
            insertTags(entity.getId(), prepared.tags());
            return entity.getId();
        } catch (DuplicateKeyException exception) {
            String id = findDuplicateId(userId, prepared.activeKey());
            FoodOptionEntity conflicted = id == null ? null : foodMapper.selectOwnedActive(userId, id);
            if (conflicted != null) {
                return conflicted.getId();
            }
            throw exception;
        }
    }

    /** 作用：规范化并校验完整写入内容。输入：原始四字段。输出：PreparedFood。逻辑：先限制原始标签数，再内容与价格校验。 */
    private PreparedFood prepare(String nameInput, String categoryInput, String priceInput, List<String> tagInput) {
        if (tagInput == null) throw validation("tags", "不能为null");
        if (tagInput.size() > 50) throw validation("tags", "最多50个标签");
        String name = normalizer.displayName(nameInput);
        String category = normalizer.displayCategory(categoryInput);
        List<String> displayTags = tagInput.stream().map(normalizer::displayTag).toList();
        validator.validateFood(name, category, displayTags);
        List<FoodNormalizer.TagValue> tags = normalizer.tags(displayTags);
        BigDecimal price = priceParser.parse(priceInput);
        String normalizedName = normalizer.normalizedName(name);
        String activeKey = normalizer.activeKey(normalizedName, normalizer.normalizedCategory(category));
        return new PreparedFood(name, normalizedName, category, price, tags, activeKey);
    }

    /** 作用：校验分页与标签筛选数量。输入：page、size、原始tags。输出：无。逻辑：非法值返回字段级400。 */
    private void validatePage(int page, int size, List<String> tags) {
        List<FieldErrorDetail> details = new ArrayList<>();
        if (page < 0) details.add(new FieldErrorDetail("page", "不能小于0"));
        if (size < 1 || size > 100) details.add(new FieldErrorDetail("size", "必须在1至100之间"));
        if (tags != null && tags.size() > 50) details.add(new FieldErrorDetail("tags", "最多50个筛选标签"));
        if (!details.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "分页参数不合法", details);
    }

    /** 作用：检查有效食物唯一键。输入：用户、规范键和可空排除 ID。输出：无。逻辑：发现重复时抛 409。 */
    private void ensureNotDuplicate(String userId, String key, String excludeId) {
        if (foodMapper.countDuplicate(userId, key, excludeId) > 0) {
            throw duplicate();
        }
    }

    /** 作用：读取已占用唯一键的食物 ID。输入：用户与唯一键。输出：可空ID。逻辑：为手工记录的重复关联提供最小查询。 */
    private String findDuplicateId(String userId, String key) {
        return foodMapper.selectDuplicateId(userId, key);
    }

    /** 作用：构造待新增主表实体。输入：用户、已校验食物和来源。输出：带 UUID 的实体。逻辑：不在此处写数据库。 */
    private FoodOptionEntity entity(String userId, PreparedFood food, String source) {
        FoodOptionEntity entity = new FoodOptionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setName(food.name());
        entity.setNormalizedName(food.normalizedName());
        entity.setCategory(food.category());
        entity.setDefaultPrice(food.price());
        entity.setSource(source);
        entity.setActiveUniqueKey(food.activeKey());
        return entity;
    }

    /** 作用：批量保存规范标签。输入：食物 ID 和标签值。输出：无。逻辑：统一生成 UUID 与 UTC 创建时间后调用 XML。 */
    private void insertTags(String foodId, List<FoodNormalizer.TagValue> tags) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<FoodOptionTagEntity> entities = tags.stream().map(tag -> {
            FoodOptionTagEntity entity = new FoodOptionTagEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setFoodOptionId(foodId);
            entity.setTag(tag.display());
            entity.setNormalizedTag(tag.normalized());
            entity.setCreatedAt(now);
            return entity;
        }).toList();
        tagMapper.insertBatch(entities);
    }

    /** 作用：读取归属食物并转换公开响应。输入：用户和食物 ID。输出：六字段响应。逻辑：无权、已删和不存在统一 404。 */
    private FoodResponses.FoodOptionData requiredResponse(String userId, String foodId) {
        FoodOptionEntity entity = foodMapper.selectOwnedActive(userId, foodId);
        if (entity == null) {
            throw notFound();
        }
        List<String> tags = tagMapper.selectByFoodIds(List.of(foodId)).stream().map(FoodTagRow::tag).toList();
        return response(entity.getId(), entity.getName(), entity.getCategory(), entity.getDefaultPrice(), tags, entity.getSource());
    }

    /** 作用：批量组织分页标签。输入：当前页食物 ID。输出：按食物分组的标签。逻辑：空页不访问数据库，避免 N+1。 */
    private Map<String, List<String>> tagsByFood(List<String> foodIds) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (foodIds.isEmpty()) {
            return result;
        }
        for (FoodTagRow row : tagMapper.selectByFoodIds(foodIds)) {
            result.computeIfAbsent(row.foodOptionId(), key -> new ArrayList<>()).add(row.tag());
        }
        return result;
    }

    /** 作用：构造公开食物响应。输入：数据库字段和标签。输出：不可变六字段 DTO。逻辑：金额统一固定两位。 */
    private FoodResponses.FoodOptionData response(String id, String name, String category, BigDecimal price, List<String> tags, String source) {
        return new FoodResponses.FoodOptionData(id, name, category, priceParser.format(price), List.copyOf(tags), source);
    }

    /** 作用：拒绝 PATCH 显式 null。输入：字段状态和字段名。输出：无。逻辑：缺失字段允许，已定义 null 返回字段级 400。 */
    private <T> void rejectNull(PatchField<T> field, String name) {
        if (field.defined() && field.value() == null) {
            throw validation(name, "不能显式为null");
        }
    }

    /** 作用：创建字段校验异常。输入：字段和原因。输出：400 ApiException。逻辑：使用统一错误模型。 */
    private ApiException validation(String field, String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不合法",
                List.of(new FieldErrorDetail(field, reason)));
    }

    /** 作用：创建资源不可见异常。输入：无。输出：404 ApiException。逻辑：隐藏资源不存在与越权的差异。 */
    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在或不属于当前用户");
    }

    /** 作用：创建重复食物异常。输入：无。输出：409 ApiException。逻辑：统一预检查和数据库唯一键冲突。 */
    private ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "FOOD_OPTION_DUPLICATE", "相同名称和分类的食物已存在");
    }

    /** 写入前完成规范化和校验的不可变食物数据。 */
    private record PreparedFood(String name, String normalizedName, String category, BigDecimal price,
                                List<FoodNormalizer.TagValue> tags, String activeKey) { }
}

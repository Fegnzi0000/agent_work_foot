package com.hyf.agent_work_foot.diet;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.FieldErrorDetail;
import com.hyf.agent_work_foot.common.PatchField;
import com.hyf.agent_work_foot.diet.entity.DietRecordEntity;
import com.hyf.agent_work_foot.diet.mapper.DietRecordMapper;
import com.hyf.agent_work_foot.diet.mapper.model.DietCategoryRow;
import com.hyf.agent_work_foot.diet.mapper.model.DietRecordRow;
import com.hyf.agent_work_foot.diet.mapper.model.DietSeriesRow;
import com.hyf.agent_work_foot.diet.mapper.model.DietSummaryRow;
import com.hyf.agent_work_foot.food.FoodContentValidator;
import com.hyf.agent_work_foot.food.FoodNormalizer;
import com.hyf.agent_work_foot.food.FoodPriceParser;
import com.hyf.agent_work_foot.food.FoodQueryService;
import com.hyf.agent_work_foot.food.FoodService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 饮食记录应用服务，编排快照写入、历史筛选、修改、软删除与消费统计。 */
@Service
public class DietService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> MEAL_TYPES = Set.of("BREAKFAST", "LUNCH", "AFTERNOON_TEA", "DINNER", "LATE_NIGHT");
    private static final Set<String> SOURCES = Set.of("MANUAL", "SLOT");
    private static final Set<String> GROUPS = Set.of("DAY", "MONTH", "YEAR");
    private final DietRecordMapper mapper;
    private final FoodQueryService foodQueryService;
    private final FoodService foodService;
    private final FoodNormalizer normalizer;
    private final FoodContentValidator contentValidator;
    private final FoodPriceParser priceParser;
    private final Clock clock;

    public DietService(DietRecordMapper mapper, FoodQueryService foodQueryService, FoodService foodService,
                       FoodNormalizer normalizer, FoodContentValidator contentValidator,
                       FoodPriceParser priceParser, Clock clock) {
        this.mapper = mapper;
        this.foodQueryService = foodQueryService;
        this.foodService = foodService;
        this.normalizer = normalizer;
        this.contentValidator = contentValidator;
        this.priceParser = priceParser;
        this.clock = clock;
    }

    /** 作用：创建手工饮食记录。输入：当前用户和二选一创建请求。输出：保存后的快照响应。逻辑：食物入池与记录写入同一事务。 */
    @Transactional
    public DietResponses.DietRecordData create(String userId, DietRequests.CreateRequest request) {
        validateCreate(request);
        BigDecimal price = priceParser.parse(request.actualPrice(), "actualPrice", "实际价格");
        String mealType = mealType(request.mealType());
        TimeValue time = time(request.eatenAt());
        ResolvedFood food;
        if (request.foodOptionId() != null) {
            FoodQueryService.FoodSnapshot snapshot = foodQueryService.findActiveSnapshot(userId, request.foodOptionId());
            if (snapshot == null) throw notFound();
            food = new ResolvedFood(snapshot.foodOptionId(), snapshot.name(), snapshot.category(), snapshot.tags());
        } else {
            food = manualFood(request.manualFood());
            if (Boolean.TRUE.equals(request.addToFoodPool())) {
                String foodId = foodService.resolveOrCreateForManualRecord(userId, food.name(), food.category(), food.tags(), price);
                food = new ResolvedFood(foodId, food.name(), food.category(), food.tags());
            }
        }
        DietRecordEntity entity = record(userId, food, price, mealType, time);
        mapper.insert(entity);
        return response(entity);
    }

    /**
     * 作用：根据已生成的Slot快照创建饮食记录。
     * 输入：用户、Spin ID、不可变食物快照和确认表单。输出：最终Diet响应。
     * 逻辑：复用金额、餐次、时间与业务日期规则，来源固定SLOT；外层Slot事务保证记录与确认状态原子提交。
     */
    @Transactional(noRollbackFor = ApiException.class)
    public DietResponses.DietRecordData createFromSlot(String userId, String spinId, SlotFoodInput food,
                                                       String actualPrice, String mealTypeInput,
                                                       OffsetDateTime eatenAt) {
        BigDecimal price = priceParser.parse(actualPrice, "actualPrice", "实际价格");
        String validMealType = mealType(mealTypeInput);
        TimeValue validTime = time(eatenAt);
        DietRecordEntity entity = record(userId,
                new ResolvedFood(food.foodOptionId(), food.name(), food.category(), List.copyOf(food.tags())),
                price, validMealType, validTime);
        entity.setSource("SLOT");
        entity.setSourceReferenceId(spinId);
        mapper.insert(entity);
        return response(entity);
    }

    /**
     * 作用：读取Slot已经关联的Diet记录并保留软删除信息。
     * 输入：用户和记录ID。输出：不存在时null，否则返回响应及删除标记。
     * 逻辑：只为Slot重复确认提供幂等结果，不作为公开历史查询入口。
     */
    public SlotRecordLookup findSlotRecord(String userId, String recordId) {
        DietRecordEntity entity = mapper.selectOwnedAny(userId, recordId);
        return entity == null ? null : new SlotRecordLookup(response(entity), entity.getDeletedAt() != null);
    }

    /** 作用：分页查询当前用户历史快照。输入：筛选和0基分页。输出：标准分页响应。逻辑：默认当前自然月且不读取food表。 */
    public DietResponses.DietRecordPageData list(String userId, int page, int size, LocalDate startDate, LocalDate endDate,
                                                  String mealType, String category, String source) {
        validatePage(page, size);
        DateRange range = range(startDate, endDate, true);
        String normalizedCategory = category == null ? null : normalizer.escapeLike(normalizer.normalizedCategory(category));
        if (mealType != null) mealType(mealType);
        if (source != null && !SOURCES.contains(source)) throw validation("source", "值不合法");
        IPage<DietRecordRow> result = mapper.selectPage(new Page<>(page + 1L, size), userId, range.start(), range.end(),
                mealType, normalizedCategory, source);
        return new DietResponses.DietRecordPageData(result.getRecords().stream().map(this::response).toList(), page, size,
                result.getTotal(), result.getPages());
    }

    /** 作用：部分修改一条饮食记录。输入：当前用户、记录ID和三态请求。输出：修改后的快照。逻辑：锁行后合并字段并保持source不可变。 */
    @Transactional
    public DietResponses.DietRecordData patch(String userId, String recordId, DietRequests.PatchRequest request) {
        if (request.empty()) throw validation("body", "至少需要提供一个可修改字段");
        rejectNull(request.foodOptionId(), "foodOptionId"); rejectNull(request.manualFood(), "manualFood");
        rejectNull(request.actualPrice(), "actualPrice"); rejectNull(request.mealType(), "mealType"); rejectNull(request.eatenAt(), "eatenAt");
        if (request.foodOptionId().defined() && request.manualFood().defined()) throw validation("food", "foodOptionId与manualFood只能二选一");
        DietRecordEntity entity = mapper.selectOwnedActiveForUpdate(userId, recordId);
        if (entity == null) throw notFound();
        if (request.foodOptionId().defined()) {
            FoodQueryService.FoodSnapshot snapshot = foodQueryService.findActiveSnapshot(userId, request.foodOptionId().value());
            if (snapshot == null) throw notFound();
            applyFood(entity, new ResolvedFood(snapshot.foodOptionId(), snapshot.name(), snapshot.category(), snapshot.tags()));
        } else if (request.manualFood().defined()) {
            applyFood(entity, manualFood(request.manualFood().value()));
        }
        if (request.actualPrice().defined()) entity.setActualPrice(priceParser.parse(request.actualPrice().value(), "actualPrice", "实际价格"));
        if (request.mealType().defined()) entity.setMealType(mealType(request.mealType().value()));
        if (request.eatenAt().defined()) {
            TimeValue time = time(request.eatenAt().value());
            entity.setEatenAt(time.utc()); entity.setBusinessDate(time.businessDate());
        }
        entity.setUpdatedAt(LocalDateTime.now(clock));
        if (mapper.updateOwnedActive(userId, entity) == 0) throw notFound();
        return response(entity);
    }

    /** 作用：软删除当前用户记录。输入：用户和记录ID。输出：无。逻辑：条件更新隐藏跨用户与重复删除差异。 */
    @Transactional
    public void delete(String userId, String recordId) {
        if (mapper.softDelete(userId, recordId, LocalDateTime.now(clock)) == 0) throw notFound();
    }

    /** 作用：统计指定日期范围消费。输入：用户、范围和粒度。输出：总览、补零趋势和分类分布。逻辑：只聚合未删除记录。 */
    public DietResponses.DietStatisticsData statistics(String userId, LocalDate startDate, LocalDate endDate, String groupBy) {
        DateRange range = range(startDate, endDate, false);
        if (!GROUPS.contains(groupBy)) throw validation("groupBy", "必须是DAY、MONTH或YEAR");
        DietSummaryRow summary = mapper.selectSummary(userId, range.start(), range.end());
        BigDecimal total = summary.totalSpent() == null ? BigDecimal.ZERO : summary.totalSpent();
        BigDecimal average = summary.recordedDays() == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(summary.recordedDays()), 2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> actual = new HashMap<>();
        for (DietSeriesRow row : mapper.selectSeries(userId, range.start(), range.end(), groupBy)) actual.put(row.period(), row.totalSpent());
        List<DietResponses.SpendingPoint> series = periods(range, groupBy).stream()
                .map(period -> new DietResponses.SpendingPoint(period, priceParser.format(actual.getOrDefault(period, BigDecimal.ZERO))))
                .toList();
        List<DietResponses.CategoryDistribution> categories = mapper.selectCategoryDistribution(userId, range.start(), range.end()).stream()
                .map(row -> new DietResponses.CategoryDistribution(row.category(), priceParser.format(row.totalSpent()), row.recordCount())).toList();
        return new DietResponses.DietStatisticsData(priceParser.format(total), summary.recordCount(), summary.recordedDays(),
                priceParser.format(average), series, categories);
    }

    private DietRecordEntity record(String userId, ResolvedFood food, BigDecimal price, String mealType, TimeValue time) {
        DietRecordEntity entity = new DietRecordEntity();
        entity.setId(UUID.randomUUID().toString()); entity.setUserId(userId); applyFood(entity, food);
        entity.setActualPrice(price); entity.setMealType(mealType); entity.setEatenAt(time.utc()); entity.setBusinessDate(time.businessDate());
        entity.setSource("MANUAL");
        return entity;
    }

    private ResolvedFood manualFood(DietRequests.ManualFood input) {
        if (input == null || input.tags() == null) throw validation("manualFood", "名称、分类和标签不能为空");
        if (input.tags().size() > 50) throw validation("manualFood.tags", "最多50个标签");
        String name = normalizer.displayName(input.name()); String category = normalizer.displayCategory(input.category());
        List<String> tags = input.tags().stream().map(normalizer::displayTag).toList();
        contentValidator.validateFood(name, category, tags);
        return new ResolvedFood(null, name, category, normalizer.tags(tags).stream().map(FoodNormalizer.TagValue::display).toList());
    }

    private void applyFood(DietRecordEntity entity, ResolvedFood food) {
        entity.setFoodOptionId(food.foodOptionId()); entity.setFoodNameSnapshot(food.name()); entity.setCategorySnapshot(food.category());
        entity.setTagsSnapshotJson(food.tags());
    }

    private TimeValue time(OffsetDateTime input) {
        if (input == null) throw validation("eatenAt", "不能为空");
        LocalDateTime utc = input.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        if (utc.isAfter(LocalDateTime.now(clock).plusMinutes(5))) throw validation("eatenAt", "不能晚于当前时间5分钟");
        return new TimeValue(utc, utc.atZone(ZoneOffset.UTC).withZoneSameInstant(BUSINESS_ZONE).toLocalDate());
    }

    private String mealType(String value) { if (!MEAL_TYPES.contains(value)) throw validation("mealType", "值不合法"); return value; }

    private DateRange range(LocalDate start, LocalDate end, boolean allowDefault) {
        if (start == null && end == null && allowDefault) { LocalDate now = LocalDate.now(clock.withZone(BUSINESS_ZONE)); return new DateRange(now.withDayOfMonth(1), now); }
        if (start == null || end == null) throw validation("dateRange", "startDate与endDate必须成对传递");
        if (start.isAfter(end)) throw validation("dateRange", "startDate不能晚于endDate");
        if (start.plusDays(365).isBefore(end)) throw validation("dateRange", "日期范围不能超过366天");
        return new DateRange(start, end);
    }

    private List<String> periods(DateRange range, String groupBy) {
        List<String> result = new ArrayList<>(); LocalDate cursor = range.start(); DateTimeFormatter format = switch (groupBy) {
            case "DAY" -> DateTimeFormatter.ISO_LOCAL_DATE; case "MONTH" -> DateTimeFormatter.ofPattern("yyyy-MM"); default -> DateTimeFormatter.ofPattern("yyyy"); };
        while (!cursor.isAfter(range.end())) { String value = format.format(cursor); if (result.isEmpty() || !result.getLast().equals(value)) result.add(value);
            cursor = switch (groupBy) { case "DAY" -> cursor.plusDays(1); case "MONTH" -> cursor.withDayOfMonth(1).plusMonths(1); default -> cursor.withDayOfYear(1).plusYears(1); }; }
        return result;
    }

    private DietResponses.DietRecordData response(DietRecordEntity entity) { return new DietResponses.DietRecordData(entity.getId(), entity.getFoodOptionId(), entity.getFoodNameSnapshot(), entity.getCategorySnapshot(), List.copyOf(entity.getTagsSnapshotJson()), priceParser.format(entity.getActualPrice()), entity.getMealType(), entity.getEatenAt().atOffset(ZoneOffset.UTC).toString(), entity.getBusinessDate().toString(), entity.getSource(), entity.getCreatedAt() == null ? null : entity.getCreatedAt().atOffset(ZoneOffset.UTC).toString(), entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().atOffset(ZoneOffset.UTC).toString()); }
    private DietResponses.DietRecordData response(DietRecordRow row) { return new DietResponses.DietRecordData(row.id(), row.foodOptionId(), row.foodNameSnapshot(), row.categorySnapshot(), List.copyOf(row.tagsSnapshotJson()), priceParser.format(row.actualPrice()), row.mealType(), row.eatenAt().atOffset(ZoneOffset.UTC).toString(), row.businessDate().toString(), row.source(), row.createdAt().atOffset(ZoneOffset.UTC).toString(), row.updatedAt().atOffset(ZoneOffset.UTC).toString()); }
    private void validateCreate(DietRequests.CreateRequest request) { if (request == null) throw validation("body", "不能为空"); boolean pool = request.foodOptionId() != null; boolean manual = request.manualFood() != null; if (pool == manual) throw validation("food", "foodOptionId与manualFood必须二选一"); if (manual && request.addToFoodPool() == null) throw validation("addToFoodPool", "手工记录必须明确传true或false"); if (pool && request.addToFoodPool() != null) throw validation("addToFoodPool", "选择食物池时不能传入"); }
    private void validatePage(int page, int size) { if (page < 0) throw validation("page", "不能小于0"); if (size < 1 || size > 100) throw validation("size", "必须在1至100之间"); }
    private <T> void rejectNull(PatchField<T> field, String name) { if (field.defined() && field.value() == null) throw validation(name, "不能显式为null"); }
    private ApiException validation(String field, String reason) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不合法", List.of(new FieldErrorDetail(field, reason))); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在或不属于当前用户"); }
    private record ResolvedFood(String foodOptionId, String name, String category, List<String> tags) { }
    private record TimeValue(LocalDateTime utc, LocalDate businessDate) { }
    private record DateRange(LocalDate start, LocalDate end) { }

    /** Slot传入Diet的不可变食物快照。 */
    public record SlotFoodInput(String foodOptionId, String name, String category, List<String> tags) { }

    /** Slot重复确认读取结果，deleted表示关联记录已经软删除。 */
    public record SlotRecordLookup(DietResponses.DietRecordData data, boolean deleted) { }
}

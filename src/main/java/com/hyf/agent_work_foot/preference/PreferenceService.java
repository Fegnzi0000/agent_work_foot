package com.hyf.agent_work_foot.preference;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.AppConstants;
import com.hyf.agent_work_foot.preference.mapper.PreferenceMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户偏好、预算和预设选项的业务服务。
 *
 * <p>负责预设归属校验、分类替换和预算历史规则；所有数据库读写均委托 PreferenceMapper，不直接处理 HTTP。</p>
 */
@Service
public class PreferenceService {
    private static final List<String> KINDS = List.of(
            "MEDICAL_ALLERGY",
            "DIETARY_RESTRICTION",
            "DISLIKE",
            "TASTE"
    );

    private final PreferenceMapper mapper;

    /** 作用：注入偏好数据访问接口。输入：PreferenceMapper。输出：服务实例。逻辑：保存依赖。 */
    public PreferenceService(PreferenceMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 作用：读取可供客户端选择的偏好预设。
     *
     * <p>输入：无。输出：按固定分类排序的预设项映射。逻辑：先初始化全部分类，保证没有预设的分类也能返回空列表。</p>
     */
    public PreferenceResponses.OptionsData options() {
        Map<String, List<PreferenceResponses.PreferenceItem>> grouped = new LinkedHashMap<>();
        KINDS.forEach(kind -> grouped.put(kind, new ArrayList<>()));
        for (PreferenceMapper.PresetRow row : mapper.selectActivePresets()) {
            grouped.get(row.kind()).add(new PreferenceResponses.PreferenceItem(
                    AppConstants.PREFERENCE_PRESET,
                    row.code(),
                    row.label()
            ));
        }
        return new PreferenceResponses.OptionsData(grouped);
    }

    /**
     * 作用：读取一个用户的完整偏好与当前预算。
     *
     * <p>输入：JWT 来源的用户 ID。输出：预算开关、每日预算和四类偏好。逻辑：按当前业务日期读取预算，再逐类转换偏好。</p>
     */
    public PreferenceResponses.PreferencesData get(String userId) {
        return data(userId, currentBudget(userId));
    }

    /**
     * 作用：保存首次引导提交的偏好和预算。
     *
     * <p>输入：当前用户 ID 与完整引导请求。输出：无。逻辑：同一事务内校验预算，按需写预算历史，并整体替换四类偏好。</p>
     */
    @Transactional
    public void submitOnboarding(String userId, PreferenceRequests.OnboardingRequest request) {
        validateBudget(request.budgetEnabled(), request.dailyBudget());
        if (request.budgetEnabled()) {
            upsertBudget(userId, true, request.dailyBudget());
        }
        replaceItems(userId, "MEDICAL_ALLERGY", request.medicalAllergies());
        replaceItems(userId, "DIETARY_RESTRICTION", request.dietaryRestrictions());
        replaceItems(userId, "DISLIKE", request.dislikes());
        replaceItems(userId, "TASTE", request.tastePreferences());
    }

    /**
     * 作用：部分更新当前用户偏好和预算。
     *
     * <p>输入：用户 ID 与补丁请求。输出：更新后的完整偏好。逻辑：仅替换请求中出现的列表；预算开关与金额按业务规则同步写入当天历史。</p>
     */
    @Transactional
    public PreferenceResponses.PreferencesData patch(
            String userId,
            PreferenceRequests.PreferencesPatchRequest request
    ) {
        if (request.budgetEnabled() != null) {
            validateBudget(request.budgetEnabled(), request.dailyBudget());
            upsertBudget(
                    userId,
                    request.budgetEnabled(),
                    request.budgetEnabled() ? request.dailyBudget() : null
            );
        } else if (request.dailyBudget() != null) {
            if (!currentBudget(userId).enabled()) {
                throw validation("未启用预算时不能设置每日预算");
            }
            upsertBudget(userId, true, request.dailyBudget());
        }
        replaceWhenPresent(userId, "MEDICAL_ALLERGY", request.medicalAllergies());
        replaceWhenPresent(userId, "DIETARY_RESTRICTION", request.dietaryRestrictions());
        replaceWhenPresent(userId, "DISLIKE", request.dislikes());
        replaceWhenPresent(userId, "TASTE", request.tastePreferences());
        return get(userId);
    }

    /** 作用：在补丁字段出现时替换对应分类。输入：用户 ID、分类、可为空列表。输出：无。逻辑：null 表示未修改，空列表表示清空分类。 */
    private void replaceWhenPresent(
            String userId,
            String kind,
            List<PreferenceRequests.PreferenceItem> items
    ) {
        if (items != null) {
            replaceItems(userId, kind, items);
        }
    }

    /**
     * 作用：整体替换一个用户的单类偏好。
     *
     * <p>输入：用户 ID、分类和完整新列表。输出：无。逻辑：先删除旧项，再校验并写入新项；调用方事务保证中途失败不留下半成品。</p>
     */
    private void replaceItems(String userId, String kind, List<PreferenceRequests.PreferenceItem> items) {
        mapper.deleteItems(userId, kind);
        for (PreferenceRequests.PreferenceItem item : items) {
            if (AppConstants.PREFERENCE_PRESET.equals(item.type())) {
                PreferenceMapper.PresetRow preset = mapper.selectActivePreset(kind, item.value());
                if (preset == null) {
                    throw validation("预设偏好不存在或不属于该分类");
                }
                mapper.insertItem(new PreferenceMapper.StoredItem(
                        UUID.randomUUID().toString(), userId, kind, AppConstants.PREFERENCE_PRESET,
                        preset.code(), null, preset.code().toLowerCase(Locale.ROOT)
                ));
            } else if (AppConstants.PREFERENCE_CUSTOM.equals(item.type())) {
                String value = item.value().trim();
                if (value.isEmpty()) {
                    throw validation("自定义偏好不能为空");
                }
                mapper.insertItem(new PreferenceMapper.StoredItem(
                        UUID.randomUUID().toString(), userId, kind, AppConstants.PREFERENCE_CUSTOM,
                        null, value, value.toLowerCase(Locale.ROOT)
                ));
            } else {
                throw validation("偏好类型必须为 PRESET 或 CUSTOM");
            }
        }
    }

    /** 作用：读取今天生效的预算。输入：用户 ID。输出：预算行，未设置时为关闭状态。逻辑：按业务日期查询，空结果转换为默认值。 */
    private PreferenceMapper.BudgetRow currentBudget(String userId) {
        PreferenceMapper.BudgetRow budget = mapper.selectCurrentBudget(userId, LocalDate.now());
        return budget == null ? new PreferenceMapper.BudgetRow(false, null) : budget;
    }

    /** 作用：写入当天预算历史。输入：用户 ID、开关和可为空金额。输出：无。逻辑：同一天覆盖，其他日期保留，以支持历史统计按日期回溯。 */
    private void upsertBudget(String userId, boolean enabled, BigDecimal budget) {
        mapper.upsertBudget(new PreferenceMapper.BudgetInsert(
                UUID.randomUUID().toString(), userId, enabled, budget, LocalDate.now()
        ));
    }

    /** 作用：组装完整偏好响应。输入：用户 ID 和已读取预算。输出：四类偏好与预算。逻辑：逐类读取，以独立列表防止分类互相混入。 */
    private PreferenceResponses.PreferencesData data(String userId, PreferenceMapper.BudgetRow budget) {
        return new PreferenceResponses.PreferencesData(
                budget.enabled(),
                budget.dailyBudget(),
                items(userId, "MEDICAL_ALLERGY"),
                items(userId, "DIETARY_RESTRICTION"),
                items(userId, "DISLIKE"),
                items(userId, "TASTE")
        );
    }

    /**
     * 作用：读取并转换一个分类的用户偏好。
     *
     * <p>输入：用户 ID 和分类。输出：面向 HTTP 的偏好列表。逻辑：预设项重新读取有效标签，自定义项直接使用保存值。</p>
     */
    private List<PreferenceResponses.PreferenceItem> items(String userId, String kind) {
        List<PreferenceResponses.PreferenceItem> result = new ArrayList<>();
        for (PreferenceMapper.ItemRow row : mapper.selectItems(userId, kind)) {
            if (AppConstants.PREFERENCE_PRESET.equals(row.sourceType())) {
                PreferenceMapper.PresetRow preset = mapper.selectActivePreset(kind, row.presetCode());
                if (preset != null) {
                    result.add(new PreferenceResponses.PreferenceItem(
                            AppConstants.PREFERENCE_PRESET, preset.code(), preset.label()
                    ));
                }
            } else {
                result.add(new PreferenceResponses.PreferenceItem(
                        AppConstants.PREFERENCE_CUSTOM, row.customValue(), row.customValue()
                ));
            }
        }
        return result;
    }

    /** 作用：校验预算开关与金额组合。输入：开关和可为空金额。输出：无或校验异常。逻辑：启用必须有金额，关闭必须不带金额。 */
    private void validateBudget(boolean enabled, BigDecimal dailyBudget) {
        if (enabled && dailyBudget == null) {
            throw validation("启用预算时必须填写每日预算");
        }
        if (!enabled && dailyBudget != null) {
            throw validation("未启用预算时每日预算必须为空");
        }
    }

    /** 作用：创建偏好参数校验异常。输入：面向客户端的说明。输出：VALIDATION_FAILED 异常。逻辑：统一使用 400 和稳定错误码。 */
    private ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
}

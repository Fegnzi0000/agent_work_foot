package com.hyf.agent_work_foot.preference;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 偏好模块的响应 DTO 集合。
 *
 * <p>只描述接口输出结构；预设标签和当前预算均由 PreferenceService 解析后填充。</p>
 */
public final class PreferenceResponses {
    /** 作用：禁止创建 DTO 容器实例。输入：无。输出：无。逻辑：仅通过嵌套 record 使用。 */
    private PreferenceResponses() {
    }

    /** 对外展示的单个偏好项，type 标记其来自预设还是自定义输入。 */
    public record PreferenceItem(String type, String value, String label) {
    }

    /** 当前用户完整偏好与当前生效预算。 */
    public record PreferencesData(
            boolean budgetEnabled,
            BigDecimal dailyBudget,
            List<PreferenceItem> medicalAllergies,
            List<PreferenceItem> dietaryRestrictions,
            List<PreferenceItem> dislikes,
            List<PreferenceItem> tastePreferences
    ) {
    }

    /** 系统预设按分类分组的响应。 */
    public record OptionsData(Map<String, List<PreferenceItem>> options) {
    }
}

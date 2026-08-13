package com.hyf.agent_work_foot.preference;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 偏好和引导接口的请求 DTO 集合。
 *
 * <p>定义 HTTP 字段格式与最大数量；预算启用状态和预设归属等跨字段规则由 PreferenceService 校验。</p>
 */
public final class PreferenceRequests {
    /** 作用：禁止创建 DTO 容器实例。输入：无。输出：无。逻辑：仅通过嵌套 record 使用。 */
    private PreferenceRequests() {
    }

    /** 单个偏好输入：type 为 PRESET 或 CUSTOM，value 为预设编码或用户输入，label 仅供前端携带。 */
    public record PreferenceItem(
            @NotBlank String type,
            @NotBlank @Size(max = 20) String value,
            @Size(max = 20) String label
    ) {
    }

    /** 首次引导请求：包含可选昵称、必填预算开关和四类必填偏好列表。 */
    public record OnboardingRequest(
            @Size(max = 20) String nickname,
            @NotNull Boolean budgetEnabled,
            @DecimalMin("0") @DecimalMax("100000") BigDecimal dailyBudget,
            @NotNull @Size(max = 50) List<@Valid PreferenceItem> medicalAllergies,
            @NotNull @Size(max = 50) List<@Valid PreferenceItem> dietaryRestrictions,
            @NotNull @Size(max = 50) List<@Valid PreferenceItem> dislikes,
            @NotNull @Size(max = 50) List<@Valid PreferenceItem> tastePreferences
    ) {
    }

    /** 偏好补丁请求：所有字段可省略；出现的列表将整体替换对应分类。 */
    public record PreferencesPatchRequest(
            Boolean budgetEnabled,
            @DecimalMin("0") @DecimalMax("100000") BigDecimal dailyBudget,
            @Size(max = 50) List<@Valid PreferenceItem> medicalAllergies,
            @Size(max = 50) List<@Valid PreferenceItem> dietaryRestrictions,
            @Size(max = 50) List<@Valid PreferenceItem> dislikes,
            @Size(max = 50) List<@Valid PreferenceItem> tastePreferences
    ) {
    }
}

package com.hyf.agent_work_foot.slot;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.hyf.agent_work_foot.common.StrictStringDeserializer;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Slot接口请求DTO，生成请求可省略，确认字段复用Diet业务规则。 */
public final class SlotRequests {
    /** 作用：阻止工具类实例化。输入：无。输出：无。逻辑：请求类型仅通过嵌套record对外提供。 */
    private SlotRequests() {
    }

    /** 重转请求；previousSpinId为空表示首次抽取。 */
    public record SpinRequest(UUID previousSpinId) { }

    /** 确认请求；金额、餐次和时间由Diet统一校验。 */
    public record ConfirmRequest(@JsonDeserialize(using = StrictStringDeserializer.class) String actualPrice,
                                 String mealType, OffsetDateTime eatenAt) { }
}

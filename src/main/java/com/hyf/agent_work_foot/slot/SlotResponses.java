package com.hyf.agent_work_foot.slot;

import java.util.List;

/** Slot公开响应DTO，不暴露候选池、概率或内部状态。 */
public final class SlotResponses {
    /** 作用：阻止工具类实例化。输入：无。输出：无。逻辑：响应类型仅通过嵌套record对外提供。 */
    private SlotResponses() {
    }

    /** 一次抽取返回的不可变食物快照。 */
    public record SelectedFood(String id, String name, String category, String defaultPrice, List<String> tags) { }

    /** 生成结果响应。 */
    public record SpinData(String spinId, SelectedFood selectedFood, String expiresAt) { }
}

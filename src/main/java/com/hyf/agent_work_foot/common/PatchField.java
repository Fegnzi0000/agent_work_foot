package com.hyf.agent_work_foot.common;

/**
 * PATCH 请求字段的存在性包装。
 *
 * <p>用于区分字段缺失和字段显式为 null；只属于接口输入模型，不进入 Entity 或 Mapper。</p>
 */
public record PatchField<T>(boolean defined, T value) {
    /** 作用：创建未出现字段。输入：无。输出：defined=false 的包装。逻辑：值保持 null。 */
    public static <T> PatchField<T> undefined() {
        return new PatchField<>(false, null);
    }

    /** 作用：创建已出现字段。输入：可为空的 JSON 字段值。输出：defined=true 的包装。逻辑：保留 null 供业务层明确拒绝。 */
    public static <T> PatchField<T> of(T value) {
        return new PatchField<>(true, value);
    }
}

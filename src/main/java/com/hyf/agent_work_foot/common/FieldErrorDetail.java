package com.hyf.agent_work_foot.common;

/**
 * 单个字段的校验错误详情。
 *
 * <p>field 使用 DTO 字段名或数组下标路径，reason 提供可直接展示或映射的失败原因。</p>
 */
public record FieldErrorDetail(String field, String reason) {
}

package com.hyf.agent_work_foot.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 金额 HTTP 字符串的解析、规范化和输出组件。
 *
 * <p>调用边界：Controller 请求 DTO 将金额保留为字符串；Service 使用本类转换为 BigDecimal；数据库仍保存为 DECIMAL。
 * 本类不处理任何业务金额的归属或预算规则。</p>
 */
@Component
public class MoneyParser {
    private static final Pattern DECIMAL = Pattern.compile("^[0-9]+(?:\\.[0-9]+)?$");
    private static final BigDecimal MAXIMUM = new BigDecimal("100000.00");

    /**
     * 作用：按食物默认价格字段解析金额。
     * 输入：defaultPrice 的 HTTP 十进制字符串。
     * 输出：两位 BigDecimal，或参数校验异常。
     * 逻辑：复用统一金额规则，同时保留食物模块的简洁调用入口。
     */
    public BigDecimal parse(String value) {
        return parse(value, "defaultPrice", "默认价格");
    }

    /**
     * 作用：将 HTTP 金额字符串转换为可安全持久化的两位金额。
     * 输入：普通无符号十进制字符串、错误字段名和面向用户的字段名称。
     * 输出：按 HALF_UP 舍入后的两位 BigDecimal，或抛出参数校验异常。
     * 逻辑：拒绝数字 JSON、符号、科学计数法和空白；先舍入，再校验范围。
     */
    public BigDecimal parse(String value, String field, String label) {
        if (value == null || value.length() > 32 || !DECIMAL.matcher(value).matches()) {
            throw invalid(field, label);
        }
        try {
            BigDecimal rounded = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
            if (rounded.compareTo(BigDecimal.ZERO) < 0 || rounded.compareTo(MAXIMUM) > 0) {
                throw invalid(field, label);
            }
            return rounded;
        } catch (NumberFormatException exception) {
            throw invalid(field, label);
        }
    }

    /**
     * 作用：将数据库金额转换为 API 金额字符串。
     * 输入：非空 BigDecimal。
     * 输出：固定两位小数字符串。
     * 逻辑：使用 HALF_UP 补齐或收敛小数位，避免 JSON 数字精度问题。
     */
    public String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 作用：构造统一金额格式错误。
     * 输入：字段名和展示名称。
     * 输出：携带字段详情的 400 业务异常。
     * 逻辑：使所有金额接口返回相同的错误码和格式规则。
     */
    private ApiException invalid(String field, String label) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                label + "不合法",
                List.of(new FieldErrorDetail(field, label + "必须是0至100000之间的普通十进制字符串"))
        );
    }
}

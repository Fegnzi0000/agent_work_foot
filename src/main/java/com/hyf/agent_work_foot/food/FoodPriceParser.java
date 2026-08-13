package com.hyf.agent_work_foot.food;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.FieldErrorDetail;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 食物默认价格字符串的解析、舍入、范围校验与输出组件。 */
@Component
public class FoodPriceParser {
    private static final Pattern DECIMAL = Pattern.compile("^[0-9]+(?:\\.[0-9]+)?$");
    private static final BigDecimal MAXIMUM = new BigDecimal("100000.00");

    /** 作用：解析并规范化价格。输入：最长32字符普通十进制字符串。输出：两位BigDecimal。逻辑：先HALF_UP再校验范围。 */
    public BigDecimal parse(String value) {
        if (value == null || value.length() > 32 || !DECIMAL.matcher(value).matches()) throw invalid();
        BigDecimal rounded;
        try { rounded = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP); }
        catch (NumberFormatException exception) { throw invalid(); }
        if (rounded.compareTo(BigDecimal.ZERO) < 0 || rounded.compareTo(MAXIMUM) > 0) throw invalid();
        return rounded;
    }

    /** 作用：格式化数据库金额。输入：BigDecimal。输出：固定两位字符串。逻辑：按HALF_UP补齐小数位。 */
    public String format(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }

    /** 作用：创建价格字段错误。输入：无。输出：400业务异常。逻辑：统一字段详情。 */
    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "默认价格不合法",
                List.of(new FieldErrorDetail("defaultPrice", "必须是0至100000之间的普通十进制字符串")));
    }
}

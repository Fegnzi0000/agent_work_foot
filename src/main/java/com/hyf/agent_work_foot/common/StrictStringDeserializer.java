package com.hyf.agent_work_foot.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * 严格 JSON 字符串反序列化器。
 *
 * <p>用于金额请求字段，拒绝 Jackson 默认的 number-to-string 隐式转换；金额值的语法、舍入和范围仍由
 * {@link MoneyParser} 负责。</p>
 */
public class StrictStringDeserializer extends JsonDeserializer<String> {
    /**
     * 作用：只读取 JSON 字符串节点。
     * 输入：当前 JSON Token 与 Jackson 上下文。
     * 输出：原始字符串，或类型不匹配异常。
     * 逻辑：拒绝数字、布尔值、对象和数组，确保 API 金额字段不会接收 JSON number。
     */
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            context.reportInputMismatch(String.class, "金额必须是 JSON 字符串");
        }
        return parser.getText();
    }
}

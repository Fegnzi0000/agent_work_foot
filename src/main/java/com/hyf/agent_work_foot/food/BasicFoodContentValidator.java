package com.hyf.agent_work_foot.food;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 一期本地食物内容校验器，检查码点长度、空白、控制字符和唯一键分隔符。 */
@Component
public class BasicFoodContentValidator implements FoodContentValidator {
    /**
     * 作用：校验完整食物并一次返回全部错误。
     * 输入：规范化前的展示名称、分类、标签。输出：合法时无返回。
     * 逻辑：名称/分类1至10码点、标签1至20码点，拒绝控制字符；名称分类额外拒绝竖线。
     */
    @Override
    public void validateFood(String name, String category, List<String> tags) {
        List<FieldErrorDetail> details = new ArrayList<>();
        validateText("name", name, 10, true, details);
        validateText("category", category, 10, true, details);
        for (int index = 0; index < tags.size(); index++) validateText("tags[" + index + "]", tags.get(index), 20, false, details);
        if (!details.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "食物内容不合法", details);
        }
    }

    /** 作用：校验单个文本。输入：字段、值、最大码点、是否禁竖线、错误集合。输出：无。逻辑：累积而非首次失败即退出。 */
    private void validateText(String field, String value, int maximum, boolean rejectSeparator, List<FieldErrorDetail> details) {
        if (value == null || value.isBlank()) {
            details.add(new FieldErrorDetail(field, "不能为空"));
            return;
        }
        int length = value.codePointCount(0, value.length());
        if (length > maximum) details.add(new FieldErrorDetail(field, "长度不能超过" + maximum + "个字符"));
        if (value.codePoints().anyMatch(this::isForbiddenControl)) details.add(new FieldErrorDetail(field, "不能包含控制字符、换行或制表符"));
        if (rejectSeparator && value.indexOf('|') >= 0) details.add(new FieldErrorDetail(field, "不能包含竖线"));
    }

    /** 作用：识别禁止字符。输入：Unicode码点。输出：是否为控制/格式/行分隔字符。逻辑：覆盖换行、制表符及不可见控制字符。 */
    private boolean isForbiddenControl(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint) || type == Character.FORMAT || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }
}

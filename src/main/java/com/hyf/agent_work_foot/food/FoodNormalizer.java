package com.hyf.agent_work_foot.food;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 食物名称、分类、标签和LIKE参数的集中规范化组件。 */
@Component
public class FoodNormalizer {
    /** 作用：整理展示名称。输入：原始名称。输出：去首尾Unicode空白值。逻辑：保留中间空白。 */
    public String displayName(String value) {
        return stripUnicodeWhitespace(value);
    }

    /** 作用：生成名称比较值。输入：展示名称。输出：删除全部Unicode空白并转小写。逻辑：逐码点过滤空白。 */
    public String normalizedName(String value) {
        if (value == null) return null;
        StringBuilder result = new StringBuilder();
        value.codePoints().filter(codePoint -> !isUnicodeWhitespace(codePoint))
                .forEach(codePoint -> result.appendCodePoint(codePoint));
        return result.toString().toLowerCase(Locale.ROOT);
    }

    /** 作用：整理分类展示值。输入：原始分类。输出：去首尾空白值。逻辑：不改变展示大小写。 */
    public String displayCategory(String value) {
        return stripUnicodeWhitespace(value);
    }

    /**
     * 作用：整理单个标签展示值。
     * 输入：可为空原始标签。输出：去除首尾 Unicode 空白后的值。
     * 逻辑：保留中间空白和原始大小写，供内容校验保留数组下标。
     */
    public String displayTag(String value) {
        return stripUnicodeWhitespace(value);
    }

    /** 作用：生成分类比较值。输入：分类。输出：trim后小写值。逻辑：用于唯一键与包含筛选。 */
    public String normalizedCategory(String value) {
        String display = stripUnicodeWhitespace(value);
        return display == null ? null : display.toLowerCase(Locale.ROOT);
    }

    /**
     * 作用：规范化并去重标签。
     * 输入：原始标签列表。输出：保留首次展示文本的标签值；空结果补“其他”。
     * 逻辑：按trim后小写值去重，并保持首次出现顺序。
     */
    public List<TagValue> tags(List<String> values) {
        Map<String, TagValue> result = new LinkedHashMap<>();
        if (values != null) {
            for (String value : values) {
                String display = displayTag(value);
                String normalized = display == null ? null : display.toLowerCase(Locale.ROOT);
                if (normalized != null) result.putIfAbsent(normalized, new TagValue(display, normalized));
            }
        }
        if (result.isEmpty()) result.put(FoodConstants.FALLBACK_TAG, new TagValue(FoodConstants.FALLBACK_TAG, FoodConstants.FALLBACK_TAG));
        return new ArrayList<>(result.values());
    }

    /** 作用：规范化查询标签。输入：可空列表。输出：去空白、去重、转小写列表。逻辑：空白项按未传处理。 */
    public List<String> filterTags(List<String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        if (values != null) for (String value : values) {
            String display = stripUnicodeWhitespace(value);
            if (display != null && !display.isEmpty()) {
                String normalized = display.toLowerCase(Locale.ROOT);
                result.putIfAbsent(normalized, normalized);
            }
        }
        return List.copyOf(result.values());
    }

    /** 作用：转义LIKE元字符。输入：规范化查询文本。输出：可安全作为包含参数的文本。逻辑：依次转义反斜杠、百分号和下划线。 */
    public String escapeLike(String value) {
        if (value == null || value.isBlank()) return null;
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 作用：生成有效食物唯一键。输入：规范名与规范分类。输出：无歧义组合键。逻辑：竖线已由内容校验禁止。 */
    public String activeKey(String normalizedName, String normalizedCategory) { return normalizedName + "|" + normalizedCategory; }

    /**
     * 作用：移除字符串首尾的全部 Unicode 空白。
     * 输入：可为空文本。输出：保留中间内容的清理结果。
     * 逻辑：同时识别 Java whitespace 与 space character，覆盖不换行空格等字符。
     */
    private String stripUnicodeWhitespace(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    /**
     * 作用：统一判断 Unicode 空白。
     * 输入：Unicode 码点。输出：是否属于空白或空格字符。
     * 逻辑：合并两个 JDK 判定口径，避免规范化遗漏不换行空格。
     */
    private boolean isUnicodeWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    /** 规范化后的标签展示值与比较值。 */
    public record TagValue(String display, String normalized) { }
}

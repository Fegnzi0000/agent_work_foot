package com.hyf.agent_work_foot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.food.BasicFoodContentValidator;
import com.hyf.agent_work_foot.food.FoodNormalizer;
import com.hyf.agent_work_foot.food.FoodPriceParser;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 食物规范化、内容和金额规则的纯单元测试，不启动 Spring 或 Docker。 */
class FoodValidationTests {
    private final FoodNormalizer normalizer = new FoodNormalizer();
    private final FoodPriceParser priceParser = new FoodPriceParser();
    private final BasicFoodContentValidator validator = new BasicFoodContentValidator();

    /** 作用：验证名称、分类和 LIKE 规范化。输入：带空白、大小写和通配符文本。输出：预期规范值。逻辑：覆盖唯一键与查询口径。 */
    @Test
    void normalizesFoodAndEscapesLikeCharacters() {
        assertEquals("ab中", normalizer.normalizedName("\u00A0A B\u3000中\u00A0"));
        assertEquals("名称", normalizer.displayName("\u00A0名称\u00A0"));
        assertEquals("fast food", normalizer.normalizedCategory(" Fast Food "));
        assertEquals("a\\%b\\_c\\\\d", normalizer.escapeLike("a%b_c\\d"));
    }

    /** 作用：验证标签去重和空标签兜底。输入：重复大小写标签与空数组。输出：首次展示文本及“其他”。逻辑：保持稳定展示语义。 */
    @Test
    void normalizesTagsAndAddsFallback() {
        assertEquals(List.of("Spicy"), normalizer.tags(List.of(" Spicy ", "spicy")).stream()
                .map(FoodNormalizer.TagValue::display).toList());
        assertEquals(List.of("其他"), normalizer.tags(List.of()).stream()
                .map(FoodNormalizer.TagValue::display).toList());
    }

    /** 作用：验证金额先舍入再判定上限。输入：边界十进制字符串。输出：固定两位或校验异常。逻辑：覆盖 HALF_UP 临界值。 */
    @Test
    void roundsPriceBeforeRangeValidation() {
        assertEquals("100000.00", priceParser.format(priceParser.parse("100000.004")));
        assertThrows(ApiException.class, () -> priceParser.parse("100000.005"));
        assertThrows(ApiException.class, () -> priceParser.parse("1e2"));
        assertThrows(ApiException.class, () -> priceParser.parse(" 12"));
    }

    /** 作用：验证 Unicode 码点和基础字符规则。输入：emoji、控制字符和竖线。输出：合法通过或错误详情。逻辑：避免按 UTF-16 长度误判。 */
    @Test
    void validatesUnicodeCodePointsAndForbiddenCharacters() {
        validator.validateFood("😀😀😀😀😀😀😀😀😀😀", "自定义", List.of("标签"));
        assertThrows(ApiException.class,
                () -> validator.validateFood("😀😀😀😀😀😀😀😀😀😀😀", "自定义|分类", List.of("坏\t标签")));
    }
}

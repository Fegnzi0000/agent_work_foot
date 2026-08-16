package com.hyf.agent_work_foot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hyf.agent_work_foot.food.FoodQueryService;
import com.hyf.agent_work_foot.slot.SlotRandomSelector;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Slot随机选择器的确定性单元测试，避免依赖概率产生偶发失败。 */
class SlotRandomSelectorTests {
    /** 作用：验证随机索引与候选严格一一对应。输入：固定返回索引1的随机源。输出：第二个候选。逻辑：生产和测试共用选择代码。 */
    @Test
    void selectsCandidateAtInjectedIndex() {
        SlotRandomSelector selector = new SlotRandomSelector(bound -> 1);
        List<FoodQueryService.FoodSnapshot> candidates = List.of(
                new FoodQueryService.FoodSnapshot("a", "A", "分类", BigDecimal.ONE, List.of("一")),
                new FoodQueryService.FoodSnapshot("b", "B", "分类", BigDecimal.TEN, List.of("二"))
        );
        assertEquals("b", selector.select(candidates).foodOptionId());
    }
}

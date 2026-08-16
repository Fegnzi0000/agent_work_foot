package com.hyf.agent_work_foot.slot;

import com.hyf.agent_work_foot.food.FoodQueryService;
import java.util.List;
import org.springframework.stereotype.Component;

/** Slot候选选择器，负责在服务已过滤的完整候选池中执行严格等概率选择。 */
@Component
public class SlotRandomSelector {
    private final RandomIndexSource randomIndexSource;

    /** 作用：注入随机索引来源。输入：可替换随机源。输出：选择器实例。逻辑：测试可注入固定序列。 */
    public SlotRandomSelector(RandomIndexSource randomIndexSource) {
        this.randomIndexSource = randomIndexSource;
    }

    /** 作用：等概率选择一个候选。输入：非空快照列表。输出：选中快照。逻辑：列表中每个索引概率相同。 */
    public FoodQueryService.FoodSnapshot select(List<FoodQueryService.FoodSnapshot> candidates) {
        return candidates.get(randomIndexSource.nextIndex(candidates.size()));
    }
}

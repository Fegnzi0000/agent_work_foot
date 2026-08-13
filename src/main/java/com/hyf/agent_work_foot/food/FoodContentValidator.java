package com.hyf.agent_work_foot.food;

import java.util.List;

/** 食物完整内容校验抽象，未来可组合微信内容安全服务。 */
public interface FoodContentValidator {
    /** 作用：一次校验完整食物。输入：展示名称、分类和标签。输出：合法时无返回；非法时抛字段级异常。逻辑：实现可收集全部问题。 */
    void validateFood(String name, String category, List<String> tags);
}

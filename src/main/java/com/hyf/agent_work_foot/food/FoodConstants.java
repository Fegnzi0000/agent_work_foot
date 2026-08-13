package com.hyf.agent_work_foot.food;

import java.util.List;

/** food模块稳定常量，包括来源、默认标签与一期预设分类。 */
public final class FoodConstants {
    public static final String SOURCE_DEFAULT = "DEFAULT";
    public static final String SOURCE_CUSTOM = "CUSTOM";
    public static final String FALLBACK_TAG = "其他";
    public static final List<String> PRESET_CATEGORIES = List.of("米饭", "面食", "粉类", "快餐", "小吃", "轻食", "汤粥", "其他");

    /** 作用：禁止实例化。输入：无。输出：无。逻辑：仅静态访问。 */
    private FoodConstants() { }
}

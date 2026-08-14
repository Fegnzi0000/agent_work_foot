package com.hyf.agent_work_foot.common;

/** 接口级权限常量，供安全解析器与方法注解共同使用。 */
public final class AppPermissions {
    public static final String FOOD_LIST = "FOOD_LIST";
    public static final String FOOD_VIEW = "FOOD_VIEW";
    public static final String FOOD_CREATE = "FOOD_CREATE";
    public static final String FOOD_UPDATE = "FOOD_UPDATE";
    public static final String FOOD_DELETE = "FOOD_DELETE";
    public static final String DIET_LIST = "DIET_LIST";
    public static final String DIET_CREATE = "DIET_CREATE";
    public static final String DIET_UPDATE = "DIET_UPDATE";
    public static final String DIET_DELETE = "DIET_DELETE";
    public static final String DIET_STATISTICS = "DIET_STATISTICS";

    /** 作用：禁止实例化。输入：无。输出：无。逻辑：权限只通过静态常量访问。 */
    private AppPermissions() {
    }
}

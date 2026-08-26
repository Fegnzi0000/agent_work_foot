package com.hyf.agent_work_foot.common;

/** 稳定接口权限编码，角色与权限的实际分配由数据库role_permissions维护。 */
public final class AppPermissions {
    public static final String ACCOUNT_SELF_VIEW = "ACCOUNT_SELF_VIEW";
    public static final String ACCOUNT_CHANGE_PASSWORD = "ACCOUNT_CHANGE_PASSWORD";
    public static final String ACCOUNT_CANCEL = "ACCOUNT_CANCEL";
    public static final String PASSWORD_CHANGE_REQUIRED_STATE = "PASSWORD_CHANGE_REQUIRED_STATE";
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
    public static final String SLOT_SPIN = "SLOT_SPIN";
    public static final String SLOT_CONFIRM = "SLOT_CONFIRM";
    /** 作用：禁止实例化。输入：无。输出：无。逻辑：权限只通过静态常量访问。 */
    private AppPermissions() {
    }
}

package com.hyf.agent_work_foot.preference.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 偏好模块的 MyBatis 数据访问接口。
 *
 * <p>负责偏好预设、用户偏好和预算历史的读写，SQL 在对应 XML；所有输入均通过 #{...} 预编译绑定。</p>
 */
public interface PreferenceMapper {
    /** 作用：读取全部启用预设。输入：无。输出：按分类和排序的预设列表。逻辑：供公开选项接口使用。 */
    List<PresetRow> selectActivePresets();

    /** 作用：验证某预设属于指定有效分类。输入：分类和预设编码。输出：预设行或 null。逻辑：供保存 PRESET 偏好时校验归属。 */
    PresetRow selectActivePreset(@Param("kind") String kind, @Param("code") String code);

    /** 作用：读取用户某一类已保存偏好。输入：用户 ID、分类。输出：按创建时间排序的偏好项。逻辑：只查询该用户数据。 */
    List<ItemRow> selectItems(@Param("userId") String userId, @Param("kind") String kind);

    /** 作用：清空用户某一类偏好。输入：用户 ID、分类。输出：无。逻辑：与后续插入共同实现整类替换。 */
    void deleteItems(@Param("userId") String userId, @Param("kind") String kind);

    /** 作用：新增用户偏好。输入：规范化后的偏好字段。输出：无。逻辑：支持预设和自定义两种来源。 */
    void insertItem(@Param("item") StoredItem item);

    /** 作用：读取业务日期当日生效的最新预算。输入：用户 ID、业务日期。输出：预算行或 null。逻辑：只取生效日不晚于业务日期的最近记录。 */
    BudgetRow selectCurrentBudget(@Param("userId") String userId, @Param("businessDate") LocalDate businessDate);

    /** 作用：写入或覆盖同一用户同一生效日预算。输入：预算历史字段。输出：无。逻辑：利用唯一键覆盖当天记录，保留其他日期历史。 */
    void upsertBudget(@Param("budget") BudgetInsert budget);

    /** 启用偏好预设的读取行。 */
    record PresetRow(String kind, String code, String label) {
    }

    /** 用户偏好的读取行，按来源携带预设编码或自定义文本。 */
    record ItemRow(String sourceType, String presetCode, String customValue) {
    }

    /** 用户偏好的插入字段，normalizedValue 用于标准化比较。 */
    record StoredItem(String id, String userId, String kind, String sourceType, String presetCode,
                      String customValue, String normalizedValue) {
    }

    /** 当前预算读取结果。 */
    record BudgetRow(boolean enabled, BigDecimal dailyBudget) {
    }

    /** 预算历史写入字段，effectiveDate 决定其开始生效日期。 */
    record BudgetInsert(String id, String userId, boolean enabled, BigDecimal dailyBudget, LocalDate effectiveDate) {
    }
}

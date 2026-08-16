package com.hyf.agent_work_foot.food.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.agent_work_foot.food.entity.FoodOptionEntity;
import com.hyf.agent_work_foot.food.mapper.model.FoodPageRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** food_options单表操作和复杂用户归属查询Mapper。 */
public interface FoodOptionMapper extends BaseMapper<FoodOptionEntity> {
    /** 作用：分页筛选有效食物。输入：分页、用户与规范化筛选。输出：主表投影页。逻辑：标签采用AND子查询。 */
    IPage<FoodPageRow> selectFoodPage(Page<FoodPageRow> page, @Param("userId") String userId,
                                      @Param("keyword") String keyword, @Param("category") String category,
                                      @Param("tags") List<String> tags, @Param("tagCount") int tagCount);

    /** 作用：读取用户有效食物详情。输入：用户ID、食物ID。输出：实体或空。逻辑：同时校验归属和软删除。 */
    FoodOptionEntity selectOwnedActive(@Param("userId") String userId, @Param("foodId") String foodId);

    /** 作用：读取用户全部有效食物。输入：用户ID。输出：按ID稳定排序的实体列表。逻辑：供Slot批量构造完整候选池。 */
    List<FoodOptionEntity> selectAllOwnedActive(@Param("userId") String userId);

    /** 作用：锁定用户有效食物。输入：用户ID、食物ID。输出：实体或空。逻辑：使用FOR UPDATE保护PATCH。 */
    FoodOptionEntity selectOwnedActiveForUpdate(@Param("userId") String userId, @Param("foodId") String foodId);

    /** 作用：统计其他重复食物。输入：用户、唯一键和排除ID。输出：数量。逻辑：仅统计有效记录。 */
    long countDuplicate(@Param("userId") String userId, @Param("activeKey") String activeKey,
                        @Param("excludeId") String excludeId);

    /** 作用：读取占用有效唯一键的食物ID。输入：用户和唯一键。输出：可空ID。逻辑：手工记录加入食物池时复用既有项。 */
    String selectDuplicateId(@Param("userId") String userId, @Param("activeKey") String activeKey);

    /** 作用：条件软删除食物。输入：归属、ID和UTC时间。输出：影响行数。逻辑：同时清空有效唯一键。 */
    int softDelete(@Param("userId") String userId, @Param("foodId") String foodId,
                   @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 作用：更新已经锁定且仍有效的用户食物。
     * 输入：当前用户 ID 与包含新字段值的实体。输出：影响行数。
     * 逻辑：再次使用用户归属和未删除条件兜底，避免并发删除后误更新资源。
     */
    int updateOwnedActive(@Param("userId") String userId, @Param("food") FoodOptionEntity food);
}

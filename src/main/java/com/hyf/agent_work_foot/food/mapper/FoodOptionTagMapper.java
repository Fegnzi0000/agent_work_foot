package com.hyf.agent_work_foot.food.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.agent_work_foot.food.entity.FoodOptionTagEntity;
import com.hyf.agent_work_foot.food.mapper.model.FoodTagRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** food_option_tags单表操作与批量标签查询Mapper。 */
public interface FoodOptionTagMapper extends BaseMapper<FoodOptionTagEntity> {
    /** 作用：批量插入标签。输入：实体列表。输出：影响行数。逻辑：XML foreach生成预编译VALUES。 */
    int insertBatch(@Param("tags") List<FoodOptionTagEntity> tags);

    /** 作用：批量读取多个食物标签。输入：食物ID列表。输出：按规范名与ID排序的投影。逻辑：避免分页N+1。 */
    List<FoodTagRow> selectByFoodIds(@Param("foodIds") List<String> foodIds);

    /** 作用：删除一个食物的全部标签。输入：食物ID。输出：影响行数。逻辑：PATCH整体替换标签时使用。 */
    int deleteByFoodId(@Param("foodId") String foodId);
}

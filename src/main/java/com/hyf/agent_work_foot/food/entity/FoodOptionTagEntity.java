package com.hyf.agent_work_foot.food.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** food_option_tags表实体，保存食物标签展示值与规范化值。 */
@TableName("food_option_tags")
@Getter
@Setter
public class FoodOptionTagEntity {
    @TableId
    private String id;
    private String foodOptionId;
    private String tag;
    private String normalizedTag;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}

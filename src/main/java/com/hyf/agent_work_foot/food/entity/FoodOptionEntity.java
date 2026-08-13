package com.hyf.agent_work_foot.food.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** food_options表实体，保存用户独立食物及软删除状态。 */
@TableName("food_options")
@Getter
@Setter
public class FoodOptionEntity {
    @TableId
    private String id;
    private String userId;
    private String name;
    private String normalizedName;
    private String category;
    private BigDecimal defaultPrice;
    private String source;
    private String activeUniqueKey;
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}

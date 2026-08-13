package com.hyf.agent_work_foot.food.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.agent_work_foot.food.mapper.JsonStringListTypeHandler;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** food_default_templates表实体，仅供新用户注册初始化读取。 */
@TableName(value = "food_default_templates", autoResultMap = true)
@Getter
@Setter
public class FoodDefaultTemplateEntity {
    @TableId
    private String id;
    private String templateVersion;
    private String name;
    private String normalizedName;
    private String category;
    private BigDecimal defaultPrice;
    @TableField(typeHandler = JsonStringListTypeHandler.class)
    private List<String> tagsJson;
    private Boolean isActive;
    private Integer sortOrder;

}

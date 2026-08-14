package com.hyf.agent_work_foot.diet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.agent_work_foot.food.mapper.JsonStringListTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** diet_records表实体，保存不可随食物池变动的饮食快照和软删除状态。 */
@TableName(value = "diet_records", autoResultMap = true)
@Getter
@Setter
public class DietRecordEntity {
    @TableId
    private String id;
    private String userId;
    private String foodOptionId;
    private String foodNameSnapshot;
    private String categorySnapshot;
    @TableField(typeHandler = JsonStringListTypeHandler.class)
    private List<String> tagsSnapshotJson;
    private BigDecimal actualPrice;
    private String mealType;
    private LocalDateTime eatenAt;
    private LocalDate businessDate;
    private String source;
    private String sourceReferenceId;
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

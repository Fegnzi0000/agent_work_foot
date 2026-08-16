package com.hyf.agent_work_foot.slot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.agent_work_foot.food.mapper.JsonStringListTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** slot_spins表实体，保存一次随机结果的完整快照、有效期和确认幂等关联。 */
@TableName(value = "slot_spins", autoResultMap = true)
@Getter
@Setter
public class SlotSpinEntity {
    @TableId
    private String id;
    private String userId;
    private String selectedFoodOptionId;
    private String selectedNameSnapshot;
    private String selectedCategorySnapshot;
    private BigDecimal selectedPriceSnapshot;
    @TableField(typeHandler = JsonStringListTypeHandler.class)
    private List<String> selectedTagsSnapshotJson;
    private String status;
    private String confirmedDietRecordId;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

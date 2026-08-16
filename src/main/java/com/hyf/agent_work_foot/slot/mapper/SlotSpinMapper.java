package com.hyf.agent_work_foot.slot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.agent_work_foot.slot.entity.SlotSpinEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/** slot_spins数据访问接口，负责用户归属行锁和状态条件更新。 */
public interface SlotSpinMapper extends BaseMapper<SlotSpinEntity> {
    /** 作用：锁定用户Spin。输入：用户和Spin ID。输出：实体或空。逻辑：序列化重转与确认状态变化。 */
    SlotSpinEntity selectOwnedForUpdate(@Param("userId") String userId, @Param("spinId") String spinId);

    /** 作用：将仍为GENERATED的结果标记过期。输入：用户、Spin和UTC时间。输出：影响行数。逻辑：条件更新避免覆盖确认状态。 */
    int markExpired(@Param("userId") String userId, @Param("spinId") String spinId,
                    @Param("updatedAt") LocalDateTime updatedAt);

    /** 作用：提交确认关联。输入：用户、Spin、Diet ID和UTC时间。输出：影响行数。逻辑：仅GENERATED可确认。 */
    int markConfirmed(@Param("userId") String userId, @Param("spinId") String spinId,
                      @Param("dietRecordId") String dietRecordId, @Param("confirmedAt") LocalDateTime confirmedAt);
}

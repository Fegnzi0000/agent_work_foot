package com.hyf.agent_work_foot.diet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.agent_work_foot.diet.entity.DietRecordEntity;
import com.hyf.agent_work_foot.diet.mapper.model.DietCategoryRow;
import com.hyf.agent_work_foot.diet.mapper.model.DietRecordRow;
import com.hyf.agent_work_foot.diet.mapper.model.DietSeriesRow;
import com.hyf.agent_work_foot.diet.mapper.model.DietSummaryRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** diet_records的单表写入、用户归属查询、分页和统计Mapper；所有动态值使用预编译绑定。 */
public interface DietRecordMapper extends BaseMapper<DietRecordEntity> {
    IPage<DietRecordRow> selectPage(Page<DietRecordRow> page, @Param("userId") String userId,
                                    @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                    @Param("mealType") String mealType, @Param("category") String category,
                                    @Param("source") String source);

    DietRecordEntity selectOwnedActiveForUpdate(@Param("userId") String userId, @Param("recordId") String recordId);

    /** 作用：读取用户记录且包含软删除状态。输入：用户和记录ID。输出：实体或空。逻辑：Slot幂等确认不得因软删除重建记录。 */
    DietRecordEntity selectOwnedAny(@Param("userId") String userId, @Param("recordId") String recordId);

    int updateOwnedActive(@Param("userId") String userId, @Param("record") DietRecordEntity record);

    int softDelete(@Param("userId") String userId, @Param("recordId") String recordId,
                   @Param("deletedAt") LocalDateTime deletedAt);

    DietSummaryRow selectSummary(@Param("userId") String userId, @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate);

    List<DietSeriesRow> selectSeries(@Param("userId") String userId, @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate, @Param("groupBy") String groupBy);

    List<DietCategoryRow> selectCategoryDistribution(@Param("userId") String userId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);
}

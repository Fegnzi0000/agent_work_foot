package com.hyf.agent_work_foot.food;

import com.hyf.agent_work_foot.food.entity.FoodOptionEntity;
import com.hyf.agent_work_foot.food.mapper.FoodOptionMapper;
import com.hyf.agent_work_foot.food.mapper.FoodOptionTagMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 后续diet与slot模块读取有效食物快照的内部服务，集中执行用户归属和软删除规则。 */
@Service
public class FoodQueryService {
    private final FoodOptionMapper foodMapper;
    private final FoodOptionTagMapper tagMapper;

    public FoodQueryService(FoodOptionMapper foodMapper, FoodOptionTagMapper tagMapper) {
        this.foodMapper = foodMapper;
        this.tagMapper = tagMapper;
    }

    /** 作用：按用户读取有效食物快照。输入：用户ID、食物ID。输出：快照或空。逻辑：只通过food Mapper访问并批量规则化标签。 */
    public FoodSnapshot findActiveSnapshot(String userId, String foodId) {
        FoodOptionEntity entity = foodMapper.selectOwnedActive(userId, foodId);
        if (entity == null) return null;
        List<String> tags = tagMapper.selectByFoodIds(List.of(foodId)).stream().map(row -> row.tag()).toList();
        return new FoodSnapshot(entity.getId(), entity.getName(), entity.getCategory(), entity.getDefaultPrice(), tags);
    }

    /**
     * 作用：批量读取当前用户完整有效食物池快照。
     * 输入：认证用户ID。输出：按食物ID稳定排序的快照列表。
     * 逻辑：主表一次查询、标签一次IN查询后在内存组装，避免Slot候选出现N+1。
     */
    public List<FoodSnapshot> findAllActiveSnapshots(String userId) {
        List<FoodOptionEntity> foods = foodMapper.selectAllOwnedActive(userId);
        if (foods.isEmpty()) return List.of();
        Map<String, List<String>> tags = new LinkedHashMap<>();
        tagMapper.selectByFoodIds(foods.stream().map(FoodOptionEntity::getId).toList())
                .forEach(row -> tags.computeIfAbsent(row.foodOptionId(), key -> new ArrayList<>()).add(row.tag()));
        return foods.stream().map(food -> new FoodSnapshot(food.getId(), food.getName(), food.getCategory(),
                food.getDefaultPrice(), List.copyOf(tags.getOrDefault(food.getId(), List.of())))).toList();
    }

    /** 下游业务使用的不可变食物快照。 */
    public record FoodSnapshot(String foodOptionId, String name, String category, BigDecimal defaultPrice, List<String> tags) { }
}

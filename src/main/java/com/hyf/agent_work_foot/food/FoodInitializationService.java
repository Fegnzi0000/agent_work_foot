package com.hyf.agent_work_foot.food;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hyf.agent_work_foot.common.MoneyParser;
import com.hyf.agent_work_foot.food.entity.FoodDefaultTemplateEntity;
import com.hyf.agent_work_foot.food.entity.FoodOptionEntity;
import com.hyf.agent_work_foot.food.entity.FoodOptionTagEntity;
import com.hyf.agent_work_foot.food.mapper.FoodDefaultTemplateMapper;
import com.hyf.agent_work_foot.food.mapper.FoodOptionMapper;
import com.hyf.agent_work_foot.food.mapper.FoodOptionTagMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 注册流程复制默认食物的模块内部服务，食物表访问不再泄漏到auth。 */
@Service
public class FoodInitializationService {
    private final FoodDefaultTemplateMapper templateMapper;
    private final FoodOptionMapper foodMapper;
    private final FoodOptionTagMapper tagMapper;
    private final FoodNormalizer normalizer;
    private final FoodContentValidator validator;
    private final MoneyParser priceParser;
    private final Clock clock;

    public FoodInitializationService(FoodDefaultTemplateMapper templateMapper, FoodOptionMapper foodMapper,
                                     FoodOptionTagMapper tagMapper, FoodNormalizer normalizer,
                                     FoodContentValidator validator, MoneyParser priceParser, Clock clock) {
        this.templateMapper = templateMapper;
        this.foodMapper = foodMapper;
        this.tagMapper = tagMapper;
        this.normalizer = normalizer;
        this.validator = validator;
        this.priceParser = priceParser;
        this.clock = clock;
    }

    /**
     * 作用：为新用户复制全部启用模板。输入：新用户ID。输出：无。
     * 逻辑：逐项规范化、校验和插入；调用者注册事务保证任一步失败整体回滚。
     */
    public void initializeDefaults(String userId) {
        List<FoodDefaultTemplateEntity> templates = templateMapper.selectList(new LambdaQueryWrapper<FoodDefaultTemplateEntity>()
                .eq(FoodDefaultTemplateEntity::getIsActive, true)
                .orderByAsc(FoodDefaultTemplateEntity::getSortOrder));
        for (FoodDefaultTemplateEntity template : templates) {
            String name = normalizer.displayName(template.getName());
            String category = normalizer.displayCategory(template.getCategory());
            List<FoodNormalizer.TagValue> tags = normalizer.tags(template.getTagsJson());
            validator.validateFood(name, category, tags.stream().map(FoodNormalizer.TagValue::display).toList());
            var price = priceParser.parse(template.getDefaultPrice().toPlainString());
            insert(userId, name, category, price, tags);
        }
    }

    /** 作用：写入一个默认食物及标签。输入：用户和已校验字段。输出：无。逻辑：UUID由应用生成，标签批量写入。 */
    private void insert(String userId, String name, String category, java.math.BigDecimal price,
                        List<FoodNormalizer.TagValue> tags) {
        String normalizedName = normalizer.normalizedName(name);
        String normalizedCategory = normalizer.normalizedCategory(category);
        FoodOptionEntity entity = new FoodOptionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setName(name);
        entity.setNormalizedName(normalizedName);
        entity.setCategory(category);
        entity.setDefaultPrice(price);
        entity.setSource(FoodConstants.SOURCE_DEFAULT);
        entity.setActiveUniqueKey(normalizer.activeKey(normalizedName, normalizedCategory));
        foodMapper.insert(entity);
        LocalDateTime now = LocalDateTime.now(clock);
        tagMapper.insertBatch(tags.stream().map(tag -> tagEntity(entity.getId(), tag, now)).toList());
    }

    /** 作用：转换规范标签为实体。输入：食物ID、标签、UTC时间。输出：标签实体。逻辑：为批量插入补齐UUID和时间。 */
    private FoodOptionTagEntity tagEntity(String foodId, FoodNormalizer.TagValue tag, LocalDateTime now) {
        FoodOptionTagEntity entity = new FoodOptionTagEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setFoodOptionId(foodId);
        entity.setTag(tag.display());
        entity.setNormalizedTag(tag.normalized());
        entity.setCreatedAt(now);
        return entity;
    }
}

package com.hyf.agent_work_foot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.agent_work_foot.auth.AuthRequests;
import com.hyf.agent_work_foot.auth.AuthResponses;
import com.hyf.agent_work_foot.auth.AuthService;
import com.hyf.agent_work_foot.food.entity.FoodDefaultTemplateEntity;
import com.hyf.agent_work_foot.food.mapper.FoodDefaultTemplateMapper;
import com.hyf.agent_work_foot.food.mapper.FoodOptionMapper;
import com.hyf.agent_work_foot.food.mapper.FoodOptionTagMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Food Mapper、JSON TypeHandler、分页和标签 AND 查询的 MySQL 集成测试。 */
class FoodMapperIntegrationTests extends AbstractMySqlIntegrationTest {
    @Autowired
    private AuthService authService;
    @Autowired
    private FoodDefaultTemplateMapper templateMapper;
    @Autowired
    private FoodOptionMapper foodMapper;
    @Autowired
    private FoodOptionTagMapper tagMapper;

    /** 作用：验证 Flyway 种子与 JSON TypeHandler。输入：V1 空库初始化结果。输出：10 条模板及列表标签。逻辑：不使用 JdbcTemplate。 */
    @Test
    void flywayAndJsonTypeHandlerInitializeTemplates() {
        assertEquals(10L, templateMapper.selectCount(null));
        FoodDefaultTemplateEntity template = templateMapper.selectById("10000000-0000-4000-8000-000000000001");
        assertEquals(List.of("主食", "肉类", "咸香"), template.getTagsJson());
    }

    /** 作用：验证注册初始化、分页 count 与多标签 AND。输入：真实注册用户。输出：默认食物页和批量标签。逻辑：整个测试事务结束后回滚。 */
    @Test
    @Transactional
    void queriesInitializedFoodsWithPaginationAndTagAndSemantics() {
        String email = "mapper+" + UUID.randomUUID() + "@example.com";
        AuthResponses.AuthData auth = authService.register(new AuthRequests.RegisterRequest(
                email, "Pass_123", "Pass_123"
        ));
        String userId = auth.user().id();

        var page = foodMapper.selectFoodPage(
                new Page<>(1, 20), userId, null, null, List.of("主食", "肉类"), 2
        );

        assertEquals(2L, page.getTotal());
        assertNotNull(foodMapper.selectOwnedActive(userId, page.getRecords().getFirst().id()));
        assertEquals(3, tagMapper.selectByFoodIds(List.of(page.getRecords().getFirst().id())).size());
    }
}

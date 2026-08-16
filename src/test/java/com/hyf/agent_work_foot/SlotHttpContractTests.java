package com.hyf.agent_work_foot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.hyf.agent_work_foot.slot.entity.SlotSpinEntity;
import com.hyf.agent_work_foot.slot.mapper.SlotSpinMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Slot生成、重转、确认幂等、候选边界与权限的HTTP契约测试。 */
class SlotHttpContractTests extends AbstractMySqlIntegrationTest {
    @Autowired
    private SlotSpinMapper slotSpinMapper;

    /** 作用：验证首次生成、确认、重复确认和已删除Diet冲突。输入：真实USER会话。输出：稳定Spin快照与唯一Diet。逻辑：覆盖主事务链路。 */
    @Test
    void generatesAndConfirmsIdempotently() throws Exception {
        String token = register("slot-life").path("data").path("accessToken").asText();
        MvcResult spinResult = perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"), null, token);
        assertEquals(200, spinResult.getResponse().getStatus());
        JsonNode spin = json(spinResult).path("data");
        String spinId = spin.path("spinId").asText();
        assertFalse(spinId.isBlank());
        assertEquals(5, spin.path("selectedFood").size());
        assertFalse(spin.path("expiresAt").asText().isBlank());

        Map<String, Object> confirmation = Map.of("actualPrice", "18.505", "mealType", "LUNCH",
                "eatenAt", "2026-08-10T12:30:00+08:00");
        MvcResult first = perform(MockMvcRequestBuilders.post("/api/v1/slot/spins/{id}/confirm", spinId), confirmation, token);
        assertEquals(200, first.getResponse().getStatus());
        JsonNode diet = json(first).path("data");
        String dietId = diet.path("id").asText();
        assertEquals("18.51", diet.path("actualPrice").asText());
        assertEquals("SLOT", diet.path("source").asText());

        MvcResult repeated = perform(MockMvcRequestBuilders.post("/api/v1/slot/spins/{id}/confirm", spinId),
                Map.of("actualPrice", "99", "mealType", "DINNER", "eatenAt", "2026-08-11T18:00:00+08:00"), token);
        assertEquals(200, repeated.getResponse().getStatus());
        assertEquals(dietId, json(repeated).path("data").path("id").asText());
        assertEquals("18.51", json(repeated).path("data").path("actualPrice").asText());

        assertEquals(204, perform(MockMvcRequestBuilders.delete("/api/v1/diet-records/{id}", dietId), null, token).getResponse().getStatus());
        MvcResult deletedRepeat = perform(MockMvcRequestBuilders.post("/api/v1/slot/spins/{id}/confirm", spinId), confirmation, token);
        assertEquals(409, deletedRepeat.getResponse().getStatus());
        assertEquals("SLOT_ALREADY_CONFIRMED_RECORD_DELETED", json(deletedRepeat).path("code").asText());
    }

    /** 作用：验证两个并发确认只创建一条Diet记录。输入：同一用户和同一Spin的两个同步请求。输出：相同Diet ID且历史数量为一。逻辑：利用行锁串行化确认并校验第二次读取既有关联。 */
    @Test
    void confirmsConcurrentlyWithoutDuplicateDiet() throws Exception {
        String token = register("slot-concurrent").path("data").path("accessToken").asText();
        String spinId = json(perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"), null, token))
                .path("data").path("spinId").asText();
        Map<String, Object> confirmation = Map.of(
                "actualPrice", "26.80",
                "mealType", "DINNER",
                "eatenAt", "2026-08-10T18:30:00+08:00"
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> firstFuture = executor.submit(() -> confirmAfterBarrier(spinId, confirmation, token, ready, start));
            Future<MvcResult> secondFuture = executor.submit(() -> confirmAfterBarrier(spinId, confirmation, token, ready, start));
            ready.await();
            start.countDown();
            MvcResult first = firstFuture.get();
            MvcResult second = secondFuture.get();
            assertEquals(200, first.getResponse().getStatus());
            assertEquals(200, second.getResponse().getStatus());
            assertEquals(json(first).path("data").path("id").asText(),
                    json(second).path("data").path("id").asText());
        } finally {
            executor.shutdownNow();
        }

        MvcResult history = perform(MockMvcRequestBuilders.get("/api/v1/diet-records")
                .param("startDate", "2026-08-10")
                .param("endDate", "2026-08-10")
                .param("source", "SLOT"), null, token);
        assertEquals(200, history.getResponse().getStatus());
        assertEquals(1, json(history).path("data").path("totalElements").asInt());
    }

    /** 作用：验证过期边界会持久化EXPIRED状态。输入：到期时间早于当前UTC时间的GENERATED Spin。输出：422及EXPIRED状态。逻辑：确认事务允许业务异常提交状态转换。 */
    @Test
    void expiresSpinAtConfirmationBoundary() throws Exception {
        String token = register("slot-expired").path("data").path("accessToken").asText();
        String spinId = json(perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"), null, token))
                .path("data").path("spinId").asText();
        SlotSpinEntity expiration = new SlotSpinEntity();
        expiration.setId(spinId);
        expiration.setExpiresAt(LocalDateTime.now(Clock.systemUTC()).minusSeconds(1));
        assertEquals(1, slotSpinMapper.updateById(expiration));

        MvcResult result = perform(MockMvcRequestBuilders.post("/api/v1/slot/spins/{id}/confirm", spinId),
                Map.of("actualPrice", "12.00", "mealType", "LUNCH",
                        "eatenAt", "2026-08-10T12:00:00+08:00"), token);
        assertEquals(422, result.getResponse().getStatus());
        assertEquals("SLOT_SPIN_EXPIRED", json(result).path("code").asText());
        assertEquals("EXPIRED", slotSpinMapper.selectById(spinId).getStatus());
    }

    /** 作用：验证确认参数错误不会污染或锁死Spin事务。输入：先非法金额、后合法表单。输出：400后仍可成功确认。逻辑：Diet校验与Slot外层事务使用一致的业务异常回滚规则。 */
    @Test
    void keepsSpinConfirmableAfterValidationFailure() throws Exception {
        String token = register("slot-validation").path("data").path("accessToken").asText();
        String spinId = json(perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"), null, token))
                .path("data").path("spinId").asText();
        MvcResult invalid = perform(MockMvcRequestBuilders.post("/api/v1/slot/spins/{id}/confirm", spinId),
                Map.of("actualPrice", "not-money", "mealType", "LUNCH",
                        "eatenAt", "2026-08-10T12:00:00+08:00"), token);
        assertEquals(400, invalid.getResponse().getStatus());
        assertEquals("VALIDATION_FAILED", json(invalid).path("code").asText());

        MvcResult valid = perform(MockMvcRequestBuilders.post("/api/v1/slot/spins/{id}/confirm", spinId),
                Map.of("actualPrice", "16.00", "mealType", "LUNCH",
                        "eatenAt", "2026-08-10T12:00:00+08:00"), token);
        assertEquals(200, valid.getResponse().getStatus());
    }

    /** 作用：验证previousSpinId只排除真实上一结果以及跨用户隐藏。输入：两个用户的Spin。输出：不同结果与404。逻辑：客户端不能直接指定排除食物。 */
    @Test
    void rerollsUsingOwnedPreviousSpin() throws Exception {
        String ownerToken = register("slot-owner").path("data").path("accessToken").asText();
        String otherToken = register("slot-other").path("data").path("accessToken").asText();
        JsonNode first = json(perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"), null, ownerToken)).path("data");
        String firstSpinId = first.path("spinId").asText();
        MvcResult reroll = perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"),
                Map.of("previousSpinId", firstSpinId), ownerToken);
        assertEquals(200, reroll.getResponse().getStatus());
        assertNotEquals(first.path("selectedFood").path("id").asText(),
                json(reroll).path("data").path("selectedFood").path("id").asText());
        assertEquals(404, perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"),
                Map.of("previousSpinId", firstSpinId), otherToken).getResponse().getStatus());
    }

    /** 作用：验证空池、单候选重转、未认证和ADMIN拒绝。输入：通过公开Food接口调整候选池。输出：422、401、403。逻辑：覆盖主要边界。 */
    @Test
    void handlesPoolEdgesAndPermissions() throws Exception {
        assertEquals(401, perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"), null, null).getResponse().getStatus());
        String admin = adminToken();
        assertEquals(403, perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"), null, admin).getResponse().getStatus());

        String oneToken = register("slot-one").path("data").path("accessToken").asText();
        JsonNode foods = json(perform(MockMvcRequestBuilders.get("/api/v1/food-options").param("size", "100"), null, oneToken))
                .path("data").path("items");
        for (int index = 1; index < foods.size(); index++) {
            assertEquals(204, perform(MockMvcRequestBuilders.delete("/api/v1/food-options/{id}", foods.get(index).path("id").asText()), null, oneToken).getResponse().getStatus());
        }
        String onlySpin = json(perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"), null, oneToken)).path("data").path("spinId").asText();
        MvcResult unavailable = perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"), Map.of("previousSpinId", onlySpin), oneToken);
        assertEquals(422, unavailable.getResponse().getStatus());
        assertEquals("SLOT_RETRY_UNAVAILABLE", json(unavailable).path("code").asText());

        assertEquals(204, perform(MockMvcRequestBuilders.delete("/api/v1/food-options/{id}", foods.get(0).path("id").asText()), null, oneToken).getResponse().getStatus());
        MvcResult empty = perform(MockMvcRequestBuilders.post("/api/v1/slot/spins"), null, oneToken);
        assertEquals(422, empty.getResponse().getStatus());
        assertEquals("FOOD_POOL_EMPTY", json(empty).path("code").asText());
    }

    /** 作用：在两个工作线程同时释放确认请求。输入：Spin、请求体、Token和同步栅栏。输出：MVC响应。逻辑：先报告就绪，再等待统一起跑以制造真实竞争。 */
    private MvcResult confirmAfterBarrier(String spinId, Map<String, Object> confirmation, String token,
                                          CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return perform(MockMvcRequestBuilders.post("/api/v1/slot/spins/{id}/confirm", spinId),
                confirmation, token);
    }
}

package com.hyf.agent_work_foot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.mysql.MySQLContainer;

/**
 * MySQL 集成测试公共基类。
 *
 * <p>统一启动 MySQL 8.4、执行 Flyway V1，并提供 MockMvc 注册和 JSON 工具；测试不依赖本机开发库。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractMySqlIntegrationTest {
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("agent_work_foot")
            .withUsername("test")
            .withPassword("test");

    static {
        MYSQL.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * 作用：将 Testcontainers 数据源接入 Spring。
     * 输入：动态配置注册器。输出：无。
     * 逻辑：覆盖 dev 数据源，并提高注册限流阈值以隔离大量 HTTP 用例。
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.auth.rate-limit.register-max-attempts", () -> 1000);
    }

    /**
     * 作用：创建独立普通用户。
     * 输入：可读的邮箱前缀。输出：注册响应 JSON。
     * 逻辑：追加 UUID 避免跨测试数据冲突，通过真实注册流程初始化默认食物。
     */
    protected JsonNode register(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.com";
        MvcResult result = perform(MockMvcRequestBuilders.post("/api/v1/auth/register"), Map.of(
                "email", email,
                "password", "Pass_123",
                "confirmPassword", "Pass_123"
        ), null);
        return json(result);
    }

    /**
     * 作用：执行带可选 JSON 请求体和 Bearer Token 的请求。
     * 输入：MockMvc 请求、可空请求体和可空 Token。输出：原始 MVC 结果。
     * 逻辑：统一序列化和认证 Header，断言由具体测试完成。
     */
    protected MvcResult perform(MockHttpServletRequestBuilder request, Object body, String token) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(body));
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    /**
     * 作用：读取 MVC 响应 JSON。
     * 输入：含响应体的 MVC 结果。输出：JsonNode。
     * 逻辑：直接解析 UTF-8 字节，避免平台默认编码影响中文断言。
     */
    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}

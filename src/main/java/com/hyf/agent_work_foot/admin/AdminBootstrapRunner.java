package com.hyf.agent_work_foot.admin;

import com.hyf.agent_work_foot.config.AdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/** bootstrap-admin一次性命令入口，仅在显式配置启用时执行，不提供网络提权接口。 */
@Component
@ConditionalOnProperty(prefix = "app.admin.bootstrap", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private final AdminBootstrapService service;
    private final AdminProperties properties;
    private final ConfigurableApplicationContext context;

    /** 作用：注入初始化服务和配置。输入：服务、AdminProperties。输出：Runner实例。逻辑：默认配置关闭时本Bean不存在。 */
    public AdminBootstrapRunner(
            AdminBootstrapService service,
            AdminProperties properties,
            ConfigurableApplicationContext context
    ) {
        this.service = service;
        this.properties = properties;
        this.context = context;
    }

    /** 作用：应用启动后执行一次受控角色提升。输入：启动参数。输出：无。逻辑：只记录用户ID和角色，不记录完整邮箱。 */
    @Override
    public void run(ApplicationArguments args) {
        String userId = service.promote(properties.bootstrap().email(), properties.bootstrap().account());
        LOGGER.info("bootstrap-admin completed userId={} role=ADMIN", userId);
        SpringApplication.exit(context, () -> 0);
    }
}

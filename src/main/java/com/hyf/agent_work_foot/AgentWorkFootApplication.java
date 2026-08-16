package com.hyf.agent_work_foot;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 应用启动入口。
 *
 * <p>负责启动 Spring Boot、扫描配置属性和业务模块 Mapper；不承载业务规则。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.hyf.agent_work_foot.**.mapper")
public class AgentWorkFootApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentWorkFootApplication.class);

    /**
     * 作用：启动后端应用。
     *
     * <p>输入：命令行启动参数，可为空数组。输出：无直接返回值，应用开始监听请求。
     * 逻辑：将参数交给 Spring Boot，由框架完成组件装配与自动配置。</p>
     */
    public static void main(String[] args) {
        LOGGER.info("[启动] 开始初始化应用");
        try {
            SpringApplication.run(AgentWorkFootApplication.class, args);
        } catch (RuntimeException exception) {
            LOGGER.error("[启动] 应用初始化失败：{}：{}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            throw exception;
        }
    }

    /** 作用：记录应用已完成初始化。输入：Spring 就绪事件。输出：无。逻辑：仅输出启动阶段，不输出配置中的敏感信息。 */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        LOGGER.info("[启动] 应用已就绪，开始接收请求");
    }

}

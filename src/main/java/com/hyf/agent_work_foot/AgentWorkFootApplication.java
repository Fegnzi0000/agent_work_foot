package com.hyf.agent_work_foot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 应用启动入口。
 *
 * <p>负责启动 Spring Boot、扫描配置属性和业务模块 Mapper；不承载业务规则。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.hyf.agent_work_foot.**.mapper")
public class AgentWorkFootApplication {

    /**
     * 作用：启动后端应用。
     *
     * <p>输入：命令行启动参数，可为空数组。输出：无直接返回值，应用开始监听请求。
     * 逻辑：将参数交给 Spring Boot，由框架完成组件装配与自动配置。</p>
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentWorkFootApplication.class, args);
    }
}

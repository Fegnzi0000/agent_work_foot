package com.hyf.agent_work_foot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentWorkFootApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentWorkFootApplication.class, args);
    }

}

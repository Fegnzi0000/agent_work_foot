package com.hyf.agent_work_foot.admin;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="app.maintenance.enabled",havingValue="true")
public class LocalMaintenanceRunner implements ApplicationRunner {
    private final LocalAccountMaintenance service;
    private final Environment environment;
    private final ConfigurableApplicationContext context;
    public LocalMaintenanceRunner(LocalAccountMaintenance service, Environment environment, ConfigurableApplicationContext context) { this.service=service; this.environment=environment; this.context=context; }
    @Override public void run(ApplicationArguments args) {
        String operation=environment.getRequiredProperty("MAINTENANCE_OPERATION");
        switch(operation) {
            case "create-admin" -> System.out.println("Administrator ready id="+service.createAdmin(environment.getRequiredProperty("MAINTENANCE_ACCOUNT"),environment.getRequiredProperty("MAINTENANCE_PASSWORD"))+" (existing password unchanged)");
            case "preview" -> System.out.println(service.preview(environment.getRequiredProperty("MAINTENANCE_USER_ID")));
            case "purge" -> { service.purgeCancelled(environment.getRequiredProperty("MAINTENANCE_USER_ID"),environment.getRequiredProperty("MAINTENANCE_CONFIRMATION")); System.out.println("Cancelled account and related online data purged. Record the deletion for backup replay."); }
            default -> throw new IllegalArgumentException("Unknown maintenance operation");
        }
        SpringApplication.exit(context,()->0);
    }
}

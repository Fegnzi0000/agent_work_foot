package com.hyf.agent_work_foot.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.Clock;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis-Plus分页、UTC时钟和实体时间填充的公共基础设施配置。 */
@Configuration
public class InfrastructureConfig {
    /** 作用：提供统一UTC时钟。输入：无。输出：UTC Clock。逻辑：业务与测试通过依赖注入取得当前时间。 */
    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }

    /** 作用：启用MySQL分页插件。输入：无。输出：MyBatisPlusInterceptor。逻辑：仅注册分页内部拦截器。 */
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 作用：自动填充实体创建与更新时间。
     * 输入：统一UTC Clock。输出：MetaObjectHandler。
     * 逻辑：插入填充createdAt/updatedAt，更新只填充updatedAt，数据库默认值保留兜底。
     */
    @Bean
    MetaObjectHandler metaObjectHandler(Clock utcClock) {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now(utcClock);
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now(utcClock));
            }
        };
    }
}

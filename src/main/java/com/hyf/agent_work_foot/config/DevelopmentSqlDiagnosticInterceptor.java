package com.hyf.agent_work_foot.config;

import java.util.List;
import java.util.Locale;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 开发环境SQL诊断：输出Mapper、参数摘要、影响行数和耗时；敏感参数始终脱敏。
 * 不在测试或生产Profile加载，避免日志泄露和无意义的生产开销。
 */
@Component
@Profile("dev")
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                org.apache.ibatis.cache.CacheKey.class, BoundSql.class
        })
})
public class DevelopmentSqlDiagnosticInterceptor implements Interceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentSqlDiagnosticInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] arguments = invocation.getArgs();
        MappedStatement statement = (MappedStatement) arguments[0];
        Object parameter = arguments[1];
        BoundSql boundSql = arguments.length == 6 ? (BoundSql) arguments[5] : statement.getBoundSql(parameter);
        long startedAt = System.nanoTime();
        try {
            Object result = invocation.proceed();
            LOGGER.info("[SQL] mapper={} durationMs={} result={} sql={} params={}",
                    statement.getId(), elapsedMillis(startedAt), resultSummary(result), normalizedSql(boundSql), parameterSummary(boundSql, parameter));
            return result;
        } catch (Throwable exception) {
            LOGGER.warn("[SQL] mapper={} durationMs={} failed={} sql={} params={}",
                    statement.getId(), elapsedMillis(startedAt), exception.getClass().getSimpleName(),
                    normalizedSql(boundSql), parameterSummary(boundSql, parameter));
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String normalizedSql(BoundSql boundSql) {
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private String resultSummary(Object result) {
        if (result instanceof List<?> list) {
            return "rows=" + list.size();
        }
        return "affectedRows=" + result;
    }

    private String parameterSummary(BoundSql boundSql, Object parameterObject) {
        if (parameterObject == null || boundSql.getParameterMappings().isEmpty()) {
            return "{}";
        }
        MetaObject metaObject = org.apache.ibatis.reflection.SystemMetaObject.forObject(parameterObject);
        return boundSql.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty() + "=" + displayValue(mapping, boundSql, metaObject, parameterObject))
                .collect(java.util.stream.Collectors.joining(", ", "{", "}"));
    }

    private String displayValue(ParameterMapping mapping, BoundSql boundSql, MetaObject metaObject, Object parameterObject) {
        String property = mapping.getProperty();
        if (isSensitive(property)) {
            return "[REDACTED]";
        }
        Object value;
        if (boundSql.hasAdditionalParameter(property)) {
            value = boundSql.getAdditionalParameter(property);
        } else if (metaObject.hasGetter(property)) {
            value = metaObject.getValue(property);
        } else {
            value = parameterObject;
        }
        String text = String.valueOf(value);
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }

    private boolean isSensitive(String property) {
        String normalized = property.toLowerCase(Locale.ROOT);
        String fieldName = normalized.substring(normalized.lastIndexOf('.') + 1);
        return fieldName.contains("password") || fieldName.contains("token") || fieldName.contains("secret")
                || fieldName.contains("authorization");
    }
}

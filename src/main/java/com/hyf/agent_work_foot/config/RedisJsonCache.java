package com.hyf.agent_work_foot.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** JSON Cache-Aside 工具：Redis 出错或数据损坏时回源，不影响主业务。 */
@Component
public class RedisJsonCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisJsonCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisCacheProperties properties;

    public RedisJsonCache(StringRedisTemplate redis, ObjectMapper objectMapper, RedisCacheProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public <T> T getOrLoad(String key, JavaType type, Duration ttl, Supplier<T> loader) {
        if (!properties.enabled()) {
            return loader.get();
        }
        try {
            String value = redis.opsForValue().get(key);
            if (value != null) {
                try {
                    return objectMapper.readValue(value, type);
                } catch (JsonProcessingException exception) {
                    LOGGER.warn("[Redis缓存] 缓存内容无效，已回源 key={}", key);
                    redis.delete(key);
                }
            }
            T loaded = loader.get();
            redis.opsForValue().set(key, objectMapper.writeValueAsString(loaded), ttl);
            return loaded;
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn("[Redis缓存] Redis 不可用，已回源 type={}", exception.getClass().getSimpleName());
            return loader.get();
        }
    }

    /** 注册事务提交后的删除动作，避免回滚时误清空仍有效的缓存。 */
    public void evictAfterCommit(String key) {
        if (!properties.enabled()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(key);
                }
            });
            return;
        }
        evict(key);
    }

    private void evict(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException exception) {
            LOGGER.warn("[Redis缓存] 删除失败，将由TTL兜底 type={}", exception.getClass().getSimpleName());
        }
    }
}

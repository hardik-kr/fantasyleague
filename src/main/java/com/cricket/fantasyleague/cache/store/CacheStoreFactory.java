package com.cricket.fantasyleague.cache.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class CacheStoreFactory {

    @Value("${fantasy.cache.strategy:1}")
    private int strategy;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private ObjectMapper cacheMapper;

    @PostConstruct
    void init() {
        cacheMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public <K, V> CacheStore<K, V> create(String namespace, Class<K> keyType, Class<V> valueType) {
        if (strategy == 2) {
            if (stringRedisTemplate == null) {
                throw new IllegalStateException(
                        "fantasy.cache.strategy=2 but Redis is not configured. Add spring.data.redis.* properties.");
            }
            return new RedisCacheStore<>(stringRedisTemplate, cacheMapper, namespace, keyType, valueType);
        }
        return new InMemoryCacheStore<>();
    }

    public boolean isRedis() {
        return strategy == 2;
    }
}

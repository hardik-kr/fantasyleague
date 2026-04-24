package com.cricket.fantasyleague.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-specific bean overrides activated only when {@code fantasy.cache.strategy=2}.
 * Standard auto-configuration creates a default StringRedisTemplate; this class
 * ensures one exists with the right serializers for the CacheStore layer.
 */
@Configuration
@ConditionalOnProperty(name = "fantasy.cache.strategy", havingValue = "2")
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}

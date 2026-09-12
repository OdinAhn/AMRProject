package com.amrproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 15단계: Redis Pub/Sub 실시간 이벤트 팬아웃 (docs/API.md §5).
 * StringRedisTemplate은 spring-boot-starter-data-redis가 자동 구성한다.
 */
@Configuration
public class RedisConfig {

    /** Task/Alarm 이벤트를 함께 발행하는 단일 채널 — eventType/aggregateType으로 구분한다. */
    public static final String EVENT_CHANNEL = "amr.events";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}

package com.amrproject.service.user;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 36단계: Redis 장애 시 로그인 시도 제한은 막지 않고 통과시킨다(fail-open). 실제 Redis 동작은 LoginRateLimitTest가 검증한다.
 */
class LoginAttemptLimiterTest {

    private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(redisTemplate, 5, 50, 900, 900);

    @Test
    void Redis에_연결할_수_없으면_로그인을_막지_않고_기록_실패도_삼킨다() {
        RedisConnectionFailureException down = new RedisConnectionFailureException("redis down");
        Mockito.when(redisTemplate.opsForValue()).thenThrow(down);
        Mockito.when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString())).thenThrow(down);
        Mockito.when(redisTemplate.delete(anyString())).thenThrow(down);

        assertThatCode(() -> limiter.checkAllowed("admin", "10.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.recordFailure("admin", "10.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.clearUsername("admin")).doesNotThrowAnyException();
    }
}

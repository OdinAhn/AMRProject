package com.amrproject.service.user;

import com.amrproject.exception.LoginRateLimitedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 36단계: 로그인 실패 횟수 제한(무차별 대입 방어).
 *
 * <ul>
 *   <li>아이디별: 실패가 {@code max-failures-per-username}(기본 5)번 쌓이면 {@code lock-seconds}(기본 15분) 동안 막는다.
 *       존재하지 않는 아이디도 똑같이 세고 막아, 응답으로 아이디 존재 여부가 드러나지 않게 한다.</li>
 *   <li>IP별: 여러 아이디를 번갈아 시도하는 경우를 막는다. 같은 사무실 NAT 뒤 작업자들이 섞일 수 있어 기준을 넉넉히 둔다(기본 50).</li>
 * </ul>
 *
 * <p>카운터는 Redis에 둬서 서버 인스턴스가 여러 대여도 공유된다. 첫 실패부터 {@code window-seconds} 안에 기준에 닿으면 잠기고,
 * 잠긴 동안의 요청은 비밀번호를 확인하지 않으므로 세지 않는다. 로그인에 성공하거나 관리자가 비밀번호를 초기화하면 아이디 카운터를 지운다.
 *
 * <p>Redis 장애 시에는 제한 없이 로그인을 허용한다(fail-open) — 장애 대응 중인 관제 운영자가 로그인하지 못하는 편이 더 위험하다고 봤다.
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);
    private static final String USER_KEY_PREFIX = "amr:login-failures:user:";
    private static final String IP_KEY_PREFIX = "amr:login-failures:ip:";

    /** 실패 1회 기록: 카운터를 올리고, TTL이 없으면 집계 창을, 기준에 닿으면 잠금 시간을 TTL로 건다(원자적으로). */
    private static final RedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if redis.call('TTL', KEYS[1]) < 0 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            if count >= tonumber(ARGV[2]) then
              redis.call('EXPIRE', KEYS[1], ARGV[3])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final int maxFailuresPerUsername;
    private final int maxFailuresPerIp;
    private final long windowSeconds;
    private final long lockSeconds;

    public LoginAttemptLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.auth.login-limit.max-failures-per-username:5}") int maxFailuresPerUsername,
            @Value("${app.auth.login-limit.max-failures-per-ip:50}") int maxFailuresPerIp,
            @Value("${app.auth.login-limit.window-seconds:900}") long windowSeconds,
            @Value("${app.auth.login-limit.lock-seconds:900}") long lockSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.maxFailuresPerUsername = maxFailuresPerUsername;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.windowSeconds = windowSeconds;
        this.lockSeconds = lockSeconds;
    }

    /** 잠겨 있으면 429 {@code LOGIN_RATE_LIMITED}. 아이디·IP 중 남은 잠금 시간이 긴 쪽을 알려준다. */
    public void checkAllowed(String username, String clientIp) {
        try {
            long retryAfter = Math.max(
                    lockedSeconds(userKey(username), maxFailuresPerUsername),
                    lockedSeconds(ipKey(clientIp), maxFailuresPerIp)
            );
            if (retryAfter > 0) {
                throw new LoginRateLimitedException(retryAfter);
            }
        } catch (LoginRateLimitedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("로그인 시도 제한 확인 실패 — 제한 없이 허용합니다: {}", e.toString());
        }
    }

    public void recordFailure(String username, String clientIp) {
        try {
            increment(userKey(username), maxFailuresPerUsername);
            increment(ipKey(clientIp), maxFailuresPerIp);
        } catch (RuntimeException e) {
            log.warn("로그인 실패 기록 실패: {}", e.toString());
        }
    }

    /** 로그인 성공·관리자 비밀번호 초기화 시 아이디 잠금을 푼다(IP 카운터는 그대로 둔다). */
    public void clearUsername(String username) {
        try {
            redisTemplate.delete(userKey(username));
        } catch (RuntimeException e) {
            log.warn("로그인 실패 기록 삭제 실패: {}", e.toString());
        }
    }

    private long lockedSeconds(String key, int maxFailures) {
        String count = redisTemplate.opsForValue().get(key);
        if (count == null || Long.parseLong(count) < maxFailures) {
            return 0;
        }
        Long ttl = redisTemplate.getExpire(key);
        return ttl != null && ttl > 0 ? ttl : lockSeconds;
    }

    private void increment(String key, int maxFailures) {
        redisTemplate.execute(RECORD_FAILURE, List.of(key),
                String.valueOf(windowSeconds), String.valueOf(maxFailures), String.valueOf(lockSeconds));
    }

    /** MySQL 기본 콜레이션이 대소문자를 구분하지 않아 "Admin"으로도 admin에 로그인되므로, 카운터도 같은 아이디로 모은다. */
    private static String userKey(String username) {
        return USER_KEY_PREFIX + username.trim().toLowerCase(Locale.ROOT);
    }

    private static String ipKey(String clientIp) {
        return IP_KEY_PREFIX + clientIp;
    }
}

package com.namastute.shared;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

// What happens when Redis is down (Learning Notes 68): per-person limits fail open; the raw count (used by OTP) fails
class RateLimiterTest {

    // A Redis that's down: every script call fails the way a lost connection does
    private final RateLimiter limits = new RateLimiter(new StringRedisTemplate() {
        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            throw new RedisConnectionFailureException("Redis is down");
        }
    });

    @Test
    void perPersonLimitsLetRequestsThroughWhenRedisIsDown() {
        assertThatCode(() -> limits.check("matches", UUID.randomUUID(), 30, Duration.ofMinutes(1))).doesNotThrowAnyException();
    }

    @Test
    void theRawCountFailsSoOtpNeverSendsUnlimited() {
        assertThatThrownBy(() -> limits.hit("rate:otp:phone:+919000000000", Duration.ofMinutes(15)))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}

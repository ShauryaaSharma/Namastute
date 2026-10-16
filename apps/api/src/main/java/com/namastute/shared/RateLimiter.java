package com.namastute.shared;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

// Fixed-window rate limits in Redis (Learning Notes 23, 68): count hits per key in a window that starts at the first hit
@Component
public class RateLimiter {

    public record Hit(long count, Duration left) {}

    // INCR + set the TTL on the first hit, as one atomic step inside Redis; also returns the time left in the window
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> HIT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return {count, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redis;

    RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // Counts one hit. If Redis fails, this throws: OTP relies on that (no codes are sent without their limits).
    public Hit hit(String key, Duration window) {
        List<?> result = redis.execute(HIT, List.of(key), String.valueOf(window.toMillis()));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Redis returned no count for " + key);
        }
        return new Hit(((Number) result.get(0)).longValue(), Duration.ofMillis(Math.max(0, ((Number) result.get(1)).longValue())));
    }

    // A per-person limit on an action: over it → 429 with Retry-After. Fails OPEN: if Redis is down the request goes
    // through (with a warning), so a Redis problem never locks everyone out of matching or planning a trip.
    public void check(String action, UUID userId, int max, Duration window) {
        Hit hit;
        try {
            hit = hit("rate:" + action + ":" + userId, window);
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, letting '{}' through: {}", action, e.getMessage());
            return;
        }
        if (hit.count() > max) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "You're going a bit fast. Try again in a minute.", Math.max(1, (hit.left().toMillis() + 999) / 1000));
        }
    }
}

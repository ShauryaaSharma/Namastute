package com.namastute.auth;

import com.namastute.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class OtpService {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final int PHONE_LIMIT = 3;
    private static final Duration PHONE_WINDOW = Duration.ofMinutes(15);
    private static final int IP_LIMIT = 10;
    private static final Duration IP_WINDOW = Duration.ofHours(1);

    // INCR + set the TTL on the first hit, run inside Redis as one atomic step
    private static final RedisScript<Long> HIT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final SmsSender sms;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    OtpService(StringRedisTemplate redis, SmsSender sms,
               @Value("${namastute.otp.hmac-secret}") String secret) {
        this.redis = redis;
        this.sms = sms;
        this.hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    void requestOtp(String phone, String ip) {
        Boolean cooldownStarted = redis.opsForValue().setIfAbsent(cooldownKey(phone), "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(cooldownStarted)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN",
                    "Wait 30 seconds before requesting another code");
        }
        if (hit("rate:otp:phone:" + phone, PHONE_WINDOW) > PHONE_LIMIT) {
            throw rateLimited();
        }
        if (hit("rate:otp:ip:" + ip, IP_WINDOW) > IP_LIMIT) {
            throw rateLimited();
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(codeKey(phone), hmac(code), OTP_TTL);
        redis.delete(triesKey(phone));
        sms.send(phone, "Your Namastute code is " + code + ". It expires in 5 minutes.");
    }

    void verify(String phone, String code) {
        String stored = redis.opsForValue().get(codeKey(phone));
        if (stored == null) {
            throw invalidCode();
        }
        if (hit(triesKey(phone), OTP_TTL) > MAX_ATTEMPTS) {
            redis.delete(List.of(codeKey(phone), triesKey(phone)));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_MANY_ATTEMPTS",
                    "Too many wrong codes, request a new one");
        }
        boolean matches = MessageDigest.isEqual(
                hmac(code).getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
        // Only the request whose DEL actually removes the code wins, so two simultaneous correct guesses can't both pass
        if (!matches || !Boolean.TRUE.equals(redis.delete(codeKey(phone)))) {
            throw invalidCode();
        }
        redis.delete(triesKey(phone));
    }

    // Counts one hit in a fixed window that starts at the first hit; returns the count so far
    private long hit(String key, Duration window) {
        Long count = redis.execute(HIT, List.of(key), String.valueOf(window.toMillis()));
        if (count == null) {
            throw new IllegalStateException("Redis returned no count for " + key);
        }
        return count;
    }

    // HMAC-SHA256 of the code with our server secret, as 64 hex characters
    private String hmac(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);   // every JVM ships it; can't happen
        }
    }

    private static String codeKey(String phone) {
        return "otp:code:" + phone;
    }

    private static String triesKey(String phone) {
        return "otp:tries:" + phone;
    }

    private static String cooldownKey(String phone) {
        return "otp:cooldown:" + phone;
    }

    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "Code is wrong or expired");
    }

    private static ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
                "Too many codes requested, try again later");
    }
}

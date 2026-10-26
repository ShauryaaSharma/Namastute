package com.namastute.auth;

import com.namastute.shared.ApiException;
import com.namastute.shared.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

// One-time login codes (Learning Notes 19–22), sent to a phone number (SMS) or an email address (Learning Notes 88).
// "to" is either one, already checked by AuthController: phone numbers start with +91, emails contain @.
@Service
class OtpService {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final int TO_LIMIT = 3;   // codes per phone number or email address
    private static final Duration TO_WINDOW = Duration.ofMinutes(15);
    private static final int IP_LIMIT = 10;
    private static final Duration IP_WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final RateLimiter limits;
    private final Optional<SmsSender> sms;   // none in production until an SMS provider exists: phone login is off
    private final EmailSender email;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    OtpService(StringRedisTemplate redis, RateLimiter limits, Optional<SmsSender> sms, EmailSender email,
               @Value("${namastute.otp.hmac-secret}") String secret) {
        this.redis = redis;
        this.limits = limits;
        this.sms = sms;
        this.email = email;
        this.hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    void requestOtp(String to, String ip) {
        boolean byEmail = to.contains("@");
        if (!byEmail && sms.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PHONE_LOGIN_UNAVAILABLE", "Log in with your email for now");
        }
        Boolean cooldownStarted = redis.opsForValue().setIfAbsent(cooldownKey(to), "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(cooldownStarted)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN",
                    "Wait 30 seconds before requesting another code");
        }
        if (hit("rate:otp:to:" + to, TO_WINDOW) > TO_LIMIT) {
            throw rateLimited();
        }
        if (hit("rate:otp:ip:" + ip, IP_WINDOW) > IP_LIMIT) {
            throw rateLimited();
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(codeKey(to), hmac(code), OTP_TTL);
        redis.delete(triesKey(to));
        String text = "Your Namastute code is " + code + ". It expires in 5 minutes.";
        try {
            if (byEmail) {
                email.send(to, "Your Namastute code: " + code,
                        text + "\n\nDidn't ask for it? Ignore this email: nobody can log in without the code.");
            } else {
                sms.get().send(to, text);
            }
        } catch (RuntimeException e) {
            redis.delete(List.of(codeKey(to), cooldownKey(to)));   // nothing was sent: they may try again straight away
            throw e;
        }
    }

    void verify(String to, String code) {
        String stored = redis.opsForValue().get(codeKey(to));
        if (stored == null) {
            throw invalidCode();
        }
        if (hit(triesKey(to), OTP_TTL) > MAX_ATTEMPTS) {
            redis.delete(List.of(codeKey(to), triesKey(to)));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_MANY_ATTEMPTS",
                    "Too many wrong codes, request a new one");
        }
        boolean matches = MessageDigest.isEqual(
                hmac(code).getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
        // Only the request whose DEL actually removes the code wins, so two simultaneous correct guesses can't both pass
        if (!matches || !Boolean.TRUE.equals(redis.delete(codeKey(to)))) {
            throw invalidCode();
        }
        redis.delete(triesKey(to));
    }

    // Counts one hit in a fixed window (shared RateLimiter); a Redis failure is thrown, so no code goes out unlimited
    private long hit(String key, Duration window) {
        return limits.hit(key, window).count();
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

    private static String codeKey(String to) {
        return "otp:code:" + to;
    }

    private static String triesKey(String to) {
        return "otp:tries:" + to;
    }

    private static String cooldownKey(String to) {
        return "otp:cooldown:" + to;
    }

    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "Code is wrong or expired");
    }

    private static ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
                "Too many codes requested, try again later");
    }
}

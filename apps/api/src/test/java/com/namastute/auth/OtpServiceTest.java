package com.namastute.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.namastute.shared.ApiException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

// Integration test: real Redis (and Postgres for the app context), fake SMS that remembers the last message
@SpringBootTest
class OtpServiceTest {

    static final String PHONE = "+919000000001";
    static final String IP = "203.0.113.7";

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        CapturingSms capturingSms() {
            return new CapturingSms();
        }
    }

    static class CapturingSms implements SmsSender {
        String lastText;

        @Override
        public void send(String phone, String text) {
            lastText = text;
        }

        String lastCode() {
            Matcher m = Pattern.compile("\\d{6}").matcher(lastText);
            assertThat(m.find()).as("SMS contains a 6-digit code").isTrue();
            return m.group();
        }
    }

    @Autowired OtpService otp;
    @Autowired StringRedisTemplate redis;
    @Autowired CapturingSms sms;

    @BeforeEach
    void cleanRedis() {
        redis.delete(List.of("otp:code:" + PHONE, "otp:tries:" + PHONE, "otp:cooldown:" + PHONE,
                "rate:otp:phone:" + PHONE, "rate:otp:ip:" + IP));
    }

    @Test
    void correctCodeWorksOnlyOnce() {
        otp.requestOtp(PHONE, IP);
        String code = sms.lastCode();

        assertThatCode(() -> otp.verify(PHONE, code)).doesNotThrowAnyException();
        assertThat(errorCode(() -> otp.verify(PHONE, code))).isEqualTo("OTP_INVALID");
    }

    @Test
    void secondRequestWithinCooldownIsRejected() {
        otp.requestOtp(PHONE, IP);

        assertThat(errorCode(() -> otp.requestOtp(PHONE, IP))).isEqualTo("OTP_COOLDOWN");
    }

    @Test
    void fourthRequestInWindowIsRateLimited() {
        for (int i = 0; i < 3; i++) {
            otp.requestOtp(PHONE, IP);
            redis.delete("otp:cooldown:" + PHONE);   // skip the 30 s wait; we're testing the window limit
        }

        assertThat(errorCode(() -> otp.requestOtp(PHONE, IP))).isEqualTo("OTP_RATE_LIMITED");
        assertThat(redis.getExpire("rate:otp:phone:" + PHONE)).as("counter has a TTL").isPositive();
    }

    @Test
    void sixthWrongGuessLocksTheCode() {
        otp.requestOtp(PHONE, IP);
        String wrong = sms.lastCode().equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThat(errorCode(() -> otp.verify(PHONE, wrong))).isEqualTo("OTP_INVALID");
        }
        assertThat(errorCode(() -> otp.verify(PHONE, wrong))).isEqualTo("OTP_TOO_MANY_ATTEMPTS");
        assertThat(redis.hasKey("otp:code:" + PHONE)).as("locked code is deleted").isFalse();
    }

    @Test
    void storesHmacNotTheCode() {
        otp.requestOtp(PHONE, IP);

        String stored = redis.opsForValue().get("otp:code:" + PHONE);
        assertThat(stored).hasSize(64).doesNotContain(sms.lastCode());
    }

    private static String errorCode(ThrowingCallable call) {
        Throwable thrown = catchThrowable(call);
        assertThat(thrown).isInstanceOf(ApiException.class);
        return ((ApiException) thrown).code();
    }
}

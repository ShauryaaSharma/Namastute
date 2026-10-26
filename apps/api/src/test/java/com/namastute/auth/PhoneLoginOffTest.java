package com.namastute.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.namastute.shared.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// Production has no SMS provider yet (Learning Notes 88): asking for a code by phone must say so clearly, before
// touching Redis or sending anything. A plain unit test: no Spring, no Redis (null would fail if it were used).
class PhoneLoginOffTest {

    @Test
    void phoneLoginAnswersUnavailableWithoutAnSmsSender() {
        OtpService otp = new OtpService(null, null, Optional.empty(), (to, subject, text) -> {}, "test-secret");

        Throwable thrown = catchThrowable(() -> otp.requestOtp("+919000000098", "203.0.113.9"));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).status().value()).isEqualTo(503);
        assertThat(((ApiException) thrown).code()).isEqualTo("PHONE_LOGIN_UNAVAILABLE");
    }
}

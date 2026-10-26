package com.namastute.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.namastute.shared.ApiException;
import com.namastute.shared.RateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

// The SMTP sender (Learning Notes 90) with a fake mail server and a fake daily counter: no Gmail, no Redis
class SmtpEmailSenderTest {

    final JavaMailSender mail = mock(JavaMailSender.class);
    final RateLimiter limits = mock(RateLimiter.class);
    final SmtpEmailSender sender = new SmtpEmailSender(mail, limits, "Namastute <namastute.app@gmail.com>", 400,
            "namastute.app@gmail.com", "app-password");

    @Test
    void sendsTheCodeFromTheAppsAddress() {
        when(limits.hit(anyString(), any())).thenReturn(new RateLimiter.Hit(1, Duration.ofHours(23)));

        sender.send("asha@example.com", "Your Namastute code: 482913", "Your Namastute code is 482913.");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getFrom()).isEqualTo("Namastute <namastute.app@gmail.com>");
        assertThat(sent.getValue().getTo()).containsExactly("asha@example.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("Your Namastute code: 482913");
    }

    @Test
    void aMailServerErrorBecomesAClearMessage() {
        when(limits.hit(anyString(), any())).thenReturn(new RateLimiter.Hit(1, Duration.ofHours(23)));
        doThrow(new MailSendException("Gmail said no")).when(mail).send(any(SimpleMailMessage.class));

        Throwable thrown = catchThrowable(() -> sender.send("asha@example.com", "s", "t"));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).status().value()).isEqualTo(503);
        assertThat(((ApiException) thrown).code()).isEqualTo("CODE_NOT_SENT");
    }

    @Test
    void theCodePastTheDailyLimitIsRefusedBeforeSending() {
        when(limits.hit(anyString(), any())).thenReturn(new RateLimiter.Hit(401, Duration.ofHours(5)));

        Throwable thrown = catchThrowable(() -> sender.send("asha@example.com", "s", "t"));

        assertThat(((ApiException) thrown).code()).isEqualTo("EMAIL_DAILY_LIMIT");
        assertThat(((ApiException) thrown).retryAfterSeconds()).isEqualTo(Duration.ofHours(5).toSeconds());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void missingSettingsStopTheServerFromStarting() {
        assertThatThrownBy(() -> new SmtpEmailSender(mail, limits, "Namastute <namastute.app@gmail.com>", 400, "namastute.app@gmail.com", ""))
                .isInstanceOf(IllegalStateException.class);
    }
}

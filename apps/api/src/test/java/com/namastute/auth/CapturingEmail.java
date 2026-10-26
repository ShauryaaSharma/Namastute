package com.namastute.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.namastute.shared.ApiException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;

// Test email sender: remembers the last email so a test can read the code, like a user opening their inbox.
// failNext = the next send fails, like SES refusing it.
class CapturingEmail implements EmailSender {

    private String lastTo, lastSubject;
    boolean failNext;

    @Override
    public void send(String to, String subject, String text) {
        if (failNext) {
            failNext = false;
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CODE_NOT_SENT", "We couldn't send the code. Try again in a minute.");
        }
        lastTo = to;
        lastSubject = subject;
    }

    String lastTo() {
        return lastTo;
    }

    String lastCode() {
        Matcher m = Pattern.compile("\\d{6}").matcher(lastSubject);
        assertThat(m.find()).as("the subject shows the 6-digit code").isTrue();
        return m.group();
    }

    // Import this in a test to replace LogEmailSender with a CapturingEmail
    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        CapturingEmail capturingEmail() {
            return new CapturingEmail();
        }
    }
}

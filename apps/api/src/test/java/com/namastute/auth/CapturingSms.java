package com.namastute.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test SMS sender: remembers the last message so a test can read the code, like a user reading their phone
class CapturingSms implements SmsSender {

    private String lastText;

    @Override
    public void send(String phone, String text) {
        lastText = text;
    }

    String lastCode() {
        Matcher m = Pattern.compile("\\d{6}").matcher(lastText);
        assertThat(m.find()).as("SMS contains a 6-digit code").isTrue();
        return m.group();
    }

    // Import this in a test to replace LogSmsSender with a CapturingSms
    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        CapturingSms capturingSms() {
            return new CapturingSms();
        }
    }
}

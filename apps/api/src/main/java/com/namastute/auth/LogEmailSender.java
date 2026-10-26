package com.namastute.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Development and staging: the email goes to the log, where the team reads the code (fake-sms covers email too)
@Component
@Profile("!prod | fake-sms")
class LogEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void send(String to, String subject, String text) {
        log.info("Email to {}: {}", to, text);
    }
}

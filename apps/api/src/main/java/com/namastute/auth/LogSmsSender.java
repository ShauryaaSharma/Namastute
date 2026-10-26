package com.namastute.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// shortcut: dev-only sender that writes the SMS to the log; a real SMS provider (with DLT) brings phone login back
@Component
@Profile("!prod | fake-sms")   // not in production, where phone login answers 503 until a real sender exists; fake-sms = staging
class LogSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public void send(String phone, String text) {
        log.info("SMS to {}: {}", phone, text);
    }
}
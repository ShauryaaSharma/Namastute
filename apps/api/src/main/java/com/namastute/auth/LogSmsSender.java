package com.namastute.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// shortcut: dev-only sender that writes the SMS to the log; replace with MSG91/Firebase before real users
@Component
@Profile("!prod | fake-sms")   // not in production (prod fails to start until a real sender exists); fake-sms = local prod test only
class LogSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public void send(String phone, String text) {
        log.info("SMS to {}: {}", phone, text);
    }
}
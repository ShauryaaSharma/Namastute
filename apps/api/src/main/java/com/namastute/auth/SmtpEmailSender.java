package com.namastute.auth;

import com.namastute.shared.ApiException;
import com.namastute.shared.RateLimiter;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// Login codes by email through any SMTP server (Learning Notes 90): today a Gmail account made only for the app, with
// an app password, so no domain is needed. The server and login are spring.mail.* (application.yaml).
// shortcut: Gmail allows about 500 emails a day, so the whole app sends at most daily-limit codes a day;
// switch to SES (EMAIL_VIA=ses, needs a domain) before real traffic.
@Component
@Profile("prod & !fake-sms")
@ConditionalOnProperty(name = "namastute.email.via", havingValue = "smtp")
class SmtpEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mail;
    private final RateLimiter limits;
    private final String from;
    private final int dailyLimit;

    SmtpEmailSender(JavaMailSender mail, RateLimiter limits,
                    @Value("${namastute.email.from}") String from,
                    @Value("${namastute.email.daily-limit}") int dailyLimit,
                    @Value("${spring.mail.username}") String username,
                    @Value("${spring.mail.password}") String password) {
        if (from.isBlank() || username.isBlank() || password.isBlank()) {   // fail at startup, not at the first login
            throw new IllegalStateException("Set EMAIL_FROM, SMTP_USERNAME and SMTP_PASSWORD (infra/AWS-SETUP.md, section 16)");
        }
        this.mail = mail;
        this.limits = limits;
        this.from = from;
        this.dailyLimit = dailyLimit;
    }

    @Override
    public void send(String to, String subject, String text) {
        // Past Gmail's limit Google blocks the account for a day and nobody could log in, so refuse a little before it
        RateLimiter.Hit today = limits.hit("rate:email:day", Duration.ofDays(1));
        if (today.count() > dailyLimit) {
            log.warn("Daily email limit reached ({}): login codes refused until the window resets", dailyLimit);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_DAILY_LIMIT",
                    "Too many people are logging in right now. Try again later.", today.left().toSeconds());
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mail.send(message);
        } catch (MailException e) {
            log.warn("SMTP didn't send a login code: {}", e.getMessage());   // the message never contains the code
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CODE_NOT_SENT", "We couldn't send the code. Try again in a minute.");
        }
    }
}

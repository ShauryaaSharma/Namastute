package com.namastute.auth;

import com.namastute.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

// Login codes by email through Amazon SES (Learning Notes 88). No keys: the server's IAM role allows ses:SendEmail,
// like S3 for ID photos. The sender's domain must be verified in SES (infra/AWS-SETUP.md, "Email login").
@Component
@Profile("prod & !fake-sms")
@ConditionalOnProperty(name = "namastute.email.via", havingValue = "ses", matchIfMissing = true)   // or SmtpEmailSender
class SesEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(SesEmailSender.class);

    private final SesV2Client ses;
    private final String from;

    SesEmailSender(@Value("${namastute.email.from}") String from, @Value("${namastute.email.region}") String region) {
        if (from.isBlank()) {   // fail at startup, not at the first login
            throw new IllegalStateException("Set EMAIL_FROM, e.g. \"Namastute <login@namastute.in>\" (infra/AWS-SETUP.md)");
        }
        this.from = from;
        this.ses = SesV2Client.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();
    }

    @Override
    public void send(String to, String subject, String text) {
        try {
            ses.sendEmail(r -> r.fromEmailAddress(from)
                    .destination(d -> d.toAddresses(to))
                    .content(c -> c.simple(m -> m
                            .subject(s -> s.data(subject).charset("UTF-8"))
                            .body(b -> b.text(t -> t.data(text).charset("UTF-8"))))));
        } catch (SdkException e) {
            log.warn("SES didn't send a login code: {}", e.getMessage());   // the message never contains the code
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CODE_NOT_SENT", "We couldn't send the code. Try again in a minute.");
        }
    }
}

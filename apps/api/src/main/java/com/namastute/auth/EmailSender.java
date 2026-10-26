package com.namastute.auth;

// Anything that can deliver an email: LogEmailSender in development and staging, SesEmailSender in production
interface EmailSender {
    void send(String to, String subject, String text);
}

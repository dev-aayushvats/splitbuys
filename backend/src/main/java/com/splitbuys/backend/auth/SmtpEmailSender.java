package com.splitbuys.backend.auth;

import com.splitbuys.backend.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends OTPs over SMTP (Gmail by default). Enabled with MAIL_ENABLED=true. */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final long ttlMinutes;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${spring.mail.username}") String username,
                           @Value("${spring.mail.password}") String password,
                           @Value("${app.mail.from}") String from,
                           @Value("${app.otp.ttl-minutes}") long ttlMinutes) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("MAIL_ENABLED=true requires MAIL_USERNAME and MAIL_PASSWORD");
        }
        this.mailSender = mailSender;
        this.from = from.isBlank() ? username : from;
        this.ttlMinutes = ttlMinutes;
    }

    @Override
    public void sendOtp(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Your SplitBuys code: " + code);
        message.setText("Your SplitBuys sign-in code is " + code + ".\n\nIt expires in " + ttlMinutes
                + " minutes. If you didn't ask for it, you can ignore this email.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Could not send OTP email to {}", email, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not send the email. Try again in a moment.");
        }
    }
}

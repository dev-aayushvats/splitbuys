package com.splitbuys.backend.auth;

import com.splitbuys.backend.common.error.DependencyFailureException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Sends OTPs over SMTP (Gmail by default). Enabled with MAIL_ENABLED=true. */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
class SmtpEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final long ttlMinutes;

    SmtpEmailSender(JavaMailSender mailSender,
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
        OtpEmail content = new OtpEmail(code, ttlMinutes);
        MimeMessage message = mailSender.createMimeMessage();
        try {
            // plain text and HTML together: a client shows the HTML if it can, and the text if it cannot
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(content.subject());
            helper.setText(content.text(), content.html());
        } catch (MessagingException e) {
            throw new DependencyFailureException("EMAIL_DELIVERY_FAILED", "Could not send the email. Try again in a moment.", e);
        }
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Could not send OTP email to {}", email, e);
            throw new DependencyFailureException("EMAIL_DELIVERY_FAILED",
                    "Could not send the email. Try again in a moment.", e);
        }
    }
}

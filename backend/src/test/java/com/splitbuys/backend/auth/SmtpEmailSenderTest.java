package com.splitbuys.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitbuys.backend.common.error.DependencyFailureException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailSenderTest {
    /** A mail sender that keeps what it was asked to send instead of connecting anywhere. */
    static class Recording extends JavaMailSenderImpl {
        final List<MimeMessage> sent = new ArrayList<>();
        boolean fail;

        @Override
        public void send(MimeMessage message) {
            if (fail) {
                throw new MailSendException("smtp down");
            }
            sent.add(message);
        }
    }

    private static void collect(Part part, List<String> types, List<String> bodies) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart multi) {
            for (int i = 0; i < multi.getCount(); i++) collect(multi.getBodyPart(i), types, bodies);
        } else {
            types.add(part.getContentType().split(";")[0].trim());
            bodies.add((String) content);
        }
    }

    @Test
    void sendsTheSubjectWithTheCodeAndBothATextAndAnHtmlPart() throws Exception {
        Recording mail = new Recording();
        new SmtpEmailSender(mail, "me@example.com", "secret", "SplitBuys <me@example.com>", 10).sendOtp("a@x.com", "482915");

        MimeMessage message = mail.sent.get(0);
        message.saveChanges(); // what the mail library does on send: fills in the content types
        assertEquals("482915 is your SplitBuys code", message.getSubject());
        assertEquals("a@x.com", message.getAllRecipients()[0].toString());
        List<String> types = new ArrayList<>(), bodies = new ArrayList<>();
        collect(message, types, bodies);
        assertEquals(List.of("text/plain", "text/html"), types);
        assertTrue(bodies.get(0).contains("482915"));
        assertTrue(bodies.get(1).contains(">482915</div>"));
    }

    @Test
    void aFailureToSendIsReportedAsADependencyFailure() {
        Recording mail = new Recording();
        mail.fail = true;
        DependencyFailureException e = org.junit.jupiter.api.Assertions.assertThrows(DependencyFailureException.class,
                () -> new SmtpEmailSender(mail, "me@example.com", "secret", "", 10).sendOtp("a@x.com", "111111"));
        assertEquals("EMAIL_DELIVERY_FAILED", e.code());
    }
}

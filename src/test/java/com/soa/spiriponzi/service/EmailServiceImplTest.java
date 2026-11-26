package com.soa.spiriponzi.service;

import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import freemarker.template.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceImplTest {

    @Test
    void supportTicketIsSentToSupportWithRequesterAsReplyTo() throws Exception {
        Configuration configuration = mock(Configuration.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        EmailServiceImpl emailService = new EmailServiceImpl(configuration, mailSender);
        ReflectionTestUtils.setField(emailService, "fromAddress", "info@spiriponzi.cloud");
        ReflectionTestUtils.setField(emailService, "supportEmail", "support@example.com");

        emailService.sendSupportTicket(
                "player@example.com", "Missing credit", "Survey reward is missing.");

        verify(mailSender).send(message);
        assertEquals("support@example.com", message.getAllRecipients()[0].toString());
        assertEquals("player@example.com", message.getReplyTo()[0].toString());
        assertEquals("Spiriponzi support request: Missing credit", message.getSubject());
        assertTrue(message.getContent().toString().contains("Survey reward is missing."));
    }
}

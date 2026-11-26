package com.soa.spiriponzi.service;

import java.io.IOException;

import freemarker.template.TemplateException;
import jakarta.mail.MessagingException;

public interface EmailService {

	void sendPasswordResetEmail(String email, String resetUrl)
			throws MessagingException, IOException, TemplateException;

	void sendActivationEmail(String email, String activationUrl)
			throws MessagingException, IOException, TemplateException;

	void sendAdminLoginCode(String email, String verificationCode) throws MessagingException;

	void sendSupportTicket(String requesterEmail, String topic, String message) throws MessagingException;

}

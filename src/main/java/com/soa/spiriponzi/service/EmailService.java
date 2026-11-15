package com.soa.spiriponzi.service;

import java.io.IOException;

import freemarker.template.TemplateException;
import jakarta.mail.MessagingException;

public interface EmailService {

	void sendPasswordResetEmail(String email, String resetUrl)
			throws MessagingException, IOException, TemplateException;

}

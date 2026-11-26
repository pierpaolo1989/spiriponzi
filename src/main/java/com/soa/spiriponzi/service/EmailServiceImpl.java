package com.soa.spiriponzi.service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

	private final Configuration configuration;
	private final JavaMailSender javaMailSender;

	@Value("${app.mail.from:info@spiriponzi.cloud}")
	private String fromAddress;

	@Value("${app.support.email:info@spiriponzi.cloud}")
	private String supportEmail;

	@Override
	public void sendPasswordResetEmail(String email, String resetUrl)
			throws MessagingException, IOException, TemplateException {
		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
		helper.setFrom(fromAddress);
		helper.setSubject("Reset your Spiriponzi password");
		helper.setTo(email);
		String emailContent = getEmailContent(resetUrl);
		helper.setText(emailContent, true);
		javaMailSender.send(mimeMessage);
	}

	@Override
	public void sendActivationEmail(String email, String activationUrl)
			throws MessagingException, IOException, TemplateException {
		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
		helper.setFrom(fromAddress);
		helper.setSubject("Activate your Spiriponzi account");
		helper.setTo(email);
		StringWriter stringWriter = new StringWriter();
		Map<String, Object> model = new HashMap<>();
		model.put("activationUrl", activationUrl);
		configuration.getTemplate("activation-email.ftlh").process(model, stringWriter);
		helper.setText(stringWriter.toString(), true);
		javaMailSender.send(mimeMessage);
	}

	@Override
	public void sendAdminLoginCode(String email, String verificationCode) throws MessagingException {
		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
		helper.setFrom(fromAddress);
		helper.setSubject("Spiriponzi admin sign-in verification code");
		helper.setTo(email);
		helper.setText("Your Spiriponzi admin verification code is " + verificationCode
				+ ". It expires in 5 minutes. If you did not try to sign in, you can ignore this email.");
		javaMailSender.send(mimeMessage);
	}

	@Override
	public void sendSupportTicket(String requesterEmail, String topic, String message) throws MessagingException {
		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
		helper.setFrom(fromAddress);
		helper.setTo(supportEmail);
		helper.setReplyTo(requesterEmail);
		helper.setSubject("Spiriponzi support request: " + topic);
		helper.setText("From: " + requesterEmail + "\nTopic: " + topic + "\n\n" + message, false);
		javaMailSender.send(mimeMessage);
	}

	String getEmailContent(String resetUrl) throws IOException, TemplateException {
		StringWriter stringWriter = new StringWriter();
		Map<String, Object> model = new HashMap<>();
		model.put("resetUrl", resetUrl);
		configuration.getTemplate("email.ftlh").process(model, stringWriter);
		return stringWriter.getBuffer().toString();
	}
}

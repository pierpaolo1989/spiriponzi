package com.soa.spiriponzi.service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.soa.spiriponzi.dto.ResetPasswordDto;

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

	@Override
	public void sendEmail(ResetPasswordDto user) throws MessagingException, IOException, TemplateException {
		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);
		helper.setSubject("Welcome To Spiriponzi.io");
		helper.setTo(user.getEmail());
		String emailContent = getEmailContent(user);
		helper.setText(emailContent, true);
		javaMailSender.send(mimeMessage);
	}

	String getEmailContent(ResetPasswordDto user) throws IOException, TemplateException {
		StringWriter stringWriter = new StringWriter();
		Map<String, Object> model = new HashMap<>();
		model.put("password", user);
		configuration.getTemplate("email.ftlh").process(model, stringWriter);
		return stringWriter.getBuffer().toString();
	}
}

package com.soa.spiriponzi.service;

import java.io.IOException;

import com.soa.spiriponzi.dto.ResetPasswordDto;

import freemarker.template.TemplateException;
import jakarta.mail.MessagingException;

public interface EmailService {

	void sendEmail(ResetPasswordDto user) throws MessagingException, IOException, TemplateException;

}

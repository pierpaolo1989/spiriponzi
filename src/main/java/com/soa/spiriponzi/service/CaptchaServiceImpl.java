package com.soa.spiriponzi.service;


import java.net.URI;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestOperations;

import com.soa.spiriponzi.exception.InvalidReCaptchaException;
import com.soa.spiriponzi.exception.ReCaptchaInvalidException;
import com.soa.spiriponzi.model.GoogleResponse;

import lombok.RequiredArgsConstructor;

@Service("captchaService")
@RequiredArgsConstructor
public class CaptchaServiceImpl extends AbstractCaptchaService {

	    private final RestOperations restTemplate;

	    private static Pattern RESPONSE_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

	    @Override
	    public void processResponse(String response) {
	        if(!responseSanityCheck(response)) {
	            throw new InvalidReCaptchaException("Response contains invalid characters");
	        }

	        URI verifyUri = URI.create(String.format(
	          "https://www.google.com/recaptcha/api/siteverify?secret=%s&response=%s&remoteip=%s",
	          getReCaptchaSecret(), response, getClientIP()));

	        GoogleResponse googleResponse = restTemplate.getForObject(verifyUri, GoogleResponse.class);

	        if(!googleResponse.isSuccess()) {
	            throw new ReCaptchaInvalidException("reCaptcha was not successfully validated");
	        }
	    }

	    protected boolean responseSanityCheck(String response) {
	        return StringUtils.hasLength(response) && RESPONSE_PATTERN.matcher(response).matches();
	    }
}
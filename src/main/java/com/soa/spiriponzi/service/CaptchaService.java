package com.soa.spiriponzi.service;

import com.soa.spiriponzi.exception.ReCaptchaInvalidException;

public interface CaptchaService {
	
	default void processResponse(final String response) throws ReCaptchaInvalidException {}
    
    String getReCaptchaSite();

    String getReCaptchaSecret();

}

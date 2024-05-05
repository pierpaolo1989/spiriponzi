package com.soa.spiriponzi.exception;

public class InvalidReCaptchaException extends RuntimeException {

    public InvalidReCaptchaException() {
        super();
    }

    public InvalidReCaptchaException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public InvalidReCaptchaException(final String message) {
        super(message);
    }

    public InvalidReCaptchaException(final Throwable cause) {
        super(cause);
    }

}
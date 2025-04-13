package com.soa.spiriponzi.exception;

public class GenericSpriponziException extends RuntimeException {

    private final String errorCode;

    public GenericSpriponziException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

}
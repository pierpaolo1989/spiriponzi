package com.soa.spiriponzi.exception;

public class ClaimException extends RuntimeException {

    private static final long serialVersionUID = 1L;

	public ClaimException() {
        super();
    }

    public ClaimException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ClaimException(final String message) {
        super(message);
    }

    public ClaimException(final Throwable cause) {
        super(cause);
    }

}
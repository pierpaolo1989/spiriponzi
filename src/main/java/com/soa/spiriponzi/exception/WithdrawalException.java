package com.soa.spiriponzi.exception;

public class WithdrawalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

	public WithdrawalException() {
        super();
    }

    public WithdrawalException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public WithdrawalException(final String message) {
        super(message);
    }

    public WithdrawalException(final Throwable cause) {
        super(cause);
    }

}
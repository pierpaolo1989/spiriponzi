package com.soa.spiriponzi.exception;

public class WithdrawalException extends RuntimeException {

    private final String errorCode;

    public WithdrawalException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // Metodi factory per casi specifici
    public static WithdrawalException insufficientPoints() {
        return new WithdrawalException("INSUFFICIENT_POINTS", "User doesn't have enough points");
    }

    public static WithdrawalException belowMinimumAmount() {
        return new WithdrawalException("BELOW_MINIMUM", "Withdrawal amount is below the minimum threshold");
    }

}
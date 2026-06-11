package com.rdas.exception;

public class SoapServiceUnavailableException extends RuntimeException {
    public SoapServiceUnavailableException(String message) {
        super(message);
    }

    public SoapServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

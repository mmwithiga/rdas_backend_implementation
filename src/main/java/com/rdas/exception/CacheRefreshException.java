package com.rdas.exception;

public class CacheRefreshException extends RuntimeException {
    public CacheRefreshException(String message, Throwable cause) {
        super(message, cause);
    }
}

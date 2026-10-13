package com.namastute.shared;

import org.springframework.http.HttpStatus;

// Thrown by any module to end a request with a given HTTP status and error code
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
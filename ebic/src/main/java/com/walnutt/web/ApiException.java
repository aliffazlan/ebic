package com.walnutt.web;

/**
 * Thrown by service-layer code (auth/match) to signal an HTTP-status-bearing
 * error. WebServer's route handlers catch this and translate it into
 * {"error": message} with the given status - callers never see a raw stack
 * trace or internal exception message.
 */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}

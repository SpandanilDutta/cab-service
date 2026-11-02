package com.cabservice.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** A business-rule failure that should be shown to the client with a specific HTTP status. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
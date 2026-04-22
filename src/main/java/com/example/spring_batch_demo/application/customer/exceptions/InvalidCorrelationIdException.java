package com.example.spring_batch_demo.application.customer.exceptions;

/**
 * Thrown when {@code correlationId} path values are not valid UUIDs.
 */
public class InvalidCorrelationIdException extends RuntimeException {

    public InvalidCorrelationIdException(String message) {
        super(message);
    }
}

package com.example.spring_batch_demo.application.customer.exceptions;

/**
 * Raised when a valid input file cannot be staged into a location readable by the batch launcher.
 */
public class InputFileStagingException extends RuntimeException {

    public InputFileStagingException(String message) {
        super(message);
    }

    public InputFileStagingException(String message, Throwable cause) {
        super(message, cause);
    }
}

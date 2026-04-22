package com.example.spring_batch_demo.application.customer.exceptions;

/**
 * Raised when the import input file location is present but cannot be read by this application process.
 */
public class InvalidInputFileResourceException extends RuntimeException {

    public InvalidInputFileResourceException(String message) {
        super(message);
    }

    public InvalidInputFileResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}

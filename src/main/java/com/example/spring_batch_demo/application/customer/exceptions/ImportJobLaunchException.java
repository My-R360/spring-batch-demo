package com.example.spring_batch_demo.application.customer.exceptions;

/**
 * Thrown when the batch job could not be started (framework-agnostic for the application port).
 */
public class ImportJobLaunchException extends Exception {

    public ImportJobLaunchException(String message, Throwable cause) {
        super(message, cause);
    }
}

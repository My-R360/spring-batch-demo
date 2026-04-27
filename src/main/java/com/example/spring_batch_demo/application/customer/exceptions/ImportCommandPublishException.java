package com.example.spring_batch_demo.application.customer.exceptions;

/**
 * Thrown when a command cannot be published to the message broker (e.g. connection refused).
 */
public class ImportCommandPublishException extends Exception {

    public ImportCommandPublishException(String message, Throwable cause) {
        super(message, cause);
    }

    public ImportCommandPublishException(String message) {
        super(message);
    }
}

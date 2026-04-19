package com.example.spring_batch_demo.application.customer.exceptions;

/**
 * Raised when the import API is called without a usable {@code inputFile} (missing or blank).
 */
public class MissingInputFileException extends RuntimeException {

    public MissingInputFileException(String message) {
        super(message);
    }

    public static MissingInputFileException forQueryParameter() {
        return new MissingInputFileException(
                "Required query parameter 'inputFile' is missing or blank. "
                        + "Provide a Spring resource location (e.g. classpath:customers.csv or file:/path/to.csv).");
    }
}

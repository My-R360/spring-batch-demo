package com.example.spring_batch_demo.application.customer;

/**
 * Shared defaults for customer CSV import so resource locations stay consistent across
 * the REST API, batch job parameters, and the item reader.
 */
public final class CustomerImportDefaults {

    public static final String DEFAULT_INPUT_RESOURCE_LOCATION = "classpath:customers.csv";

    private CustomerImportDefaults() {
    }

    /**
     * Returns a trimmed non-blank {@code inputFile}, or {@link #DEFAULT_INPUT_RESOURCE_LOCATION}.
     */
    public static String resolveInputFileLocation(String inputFile) {
        if (inputFile == null || inputFile.isBlank()) {
            return DEFAULT_INPUT_RESOURCE_LOCATION;
        }
        return inputFile.trim();
    }
}

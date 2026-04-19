package com.example.spring_batch_demo.application.customer;

import com.example.spring_batch_demo.application.customer.exceptions.MissingInputFileException;

/**
 * Normalizes and validates the customer CSV {@code inputFile} resource location for launch and for
 * the batch reader job parameter. There is <strong>no default file</strong>: callers must supply a path.
 */
public final class CustomerImportInputFile {

    private CustomerImportInputFile() {
    }

    /**
     * Validates the API / launch path: {@code inputFile} must be present and non-blank after trim.
     *
     * @throws MissingInputFileException if null or blank
     */
    public static String requireInputFileLocation(String inputFile) {
        if (inputFile == null || inputFile.isBlank()) {
            throw MissingInputFileException.forQueryParameter();
        }
        return inputFile.trim();
    }

    /**
     * Validates the job parameter read by the {@code @StepScope} reader. After a successful
     * {@code launchImport}, this should never fail; if it does, it indicates inconsistent wiring.
     */
    public static String requireJobParameterInputFile(String inputFile) {
        if (inputFile == null || inputFile.isBlank()) {
            throw new IllegalStateException(
                    "Job parameter 'inputFile' is missing or blank; launchImport must set a non-blank value.");
        }
        return inputFile.trim();
    }
}

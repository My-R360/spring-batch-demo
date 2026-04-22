package com.example.spring_batch_demo.application.customer.port;

import com.example.spring_batch_demo.application.customer.exceptions.InvalidInputFileResourceException;
import com.example.spring_batch_demo.application.customer.exceptions.MissingInputFileException;

/**
 * Validates that a customer import resource location can be consumed by the runtime.
 */
public interface CustomerImportInputFileValidator {

    /**
     * Ensures the supplied location is non-blank and points to a readable resource.
     *
     * @param inputFile Spring resource location string such as {@code classpath:customers.csv}
     *                  or {@code file:/absolute/path/customers.csv}
     * @throws MissingInputFileException if {@code inputFile} is null or blank
     * @throws InvalidInputFileResourceException if the resource does not exist or is unreadable
     */
    void validateAvailable(String inputFile);
}

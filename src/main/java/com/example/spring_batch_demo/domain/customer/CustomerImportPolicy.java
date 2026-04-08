package com.example.spring_batch_demo.domain.customer;

/**
 * Domain rule for whether and how a customer row should be imported.
 *
 * Returning {@code null} means "filtered out" for the import use-case.
 */
public interface CustomerImportPolicy {
    Customer apply(Customer input);
}


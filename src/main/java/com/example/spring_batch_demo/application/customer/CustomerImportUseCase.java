package com.example.spring_batch_demo.application.customer;

/**
 * Application-level use case: import customers from an input file into persistence.
 *
 * The concrete implementation may delegate to Spring Batch (infrastructure), but callers
 * (e.g. REST controllers) depend only on this interface.
 */
public interface CustomerImportUseCase {
    /**
     * Launches an import run.
     *
     * <p>In this project, the default implementation delegates to Spring Batch (infrastructure).
     * The input is a Spring Resource location string so callers can reference either classpath or
     * filesystem files.</p>
     *
     * @param inputFile resource location (e.g. {@code classpath:customers.csv} or {@code file:/...})
     * @return an immutable result containing execution id, status, and failure messages
     */
    CustomerImportResult importCustomers(String inputFile) throws Exception;
}


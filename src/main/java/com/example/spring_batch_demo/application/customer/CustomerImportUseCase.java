package com.example.spring_batch_demo.application.customer;

/**
 * Application-level use case: import customers from an input file into persistence.
 *
 * The concrete implementation may delegate to Spring Batch (infrastructure), but callers
 * (e.g. REST controllers) depend only on this interface.
 */
public interface CustomerImportUseCase {

    /**
     * Launches an import asynchronously and returns the job execution ID immediately.
     *
     * @param inputFile resource location (e.g. {@link CustomerImportDefaults#DEFAULT_INPUT_RESOURCE_LOCATION}
     * or {@code file:/...}); null or blank uses that default
     * @return the Spring Batch {@code jobExecutionId} that can be used to poll status
     */
    Long launchImport(String inputFile) throws Exception;

    /**
     * Queries the current status/progress of a previously launched import.
     *
     * @param jobExecutionId the ID returned by {@link #launchImport}
     * @return an immutable result with execution status, progress counts, and failure messages
     */
    CustomerImportResult getImportStatus(Long jobExecutionId);
}


package com.example.spring_batch_demo.application.customer.port;

import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.exceptions.MissingInputFileException;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;

/**
 * Application-level use case: import customers from an input file into persistence.
 *
 * <p>The concrete implementation may delegate to Spring Batch (infrastructure), but callers
 * (e.g. REST controllers) depend only on this interface.</p>
 */
public interface CustomerImportUseCase {

    /**
     * Launches an import asynchronously and returns the job execution ID immediately.
     *
     * @param inputFile required non-blank Spring resource location (e.g. {@code classpath:customers.csv}
     * or {@code file:/...}); null or blank is rejected before launch
     * @return the Spring Batch {@code jobExecutionId} that can be used to poll status
     * @throws MissingInputFileException if {@code inputFile} is null or blank
     * @throws ImportJobLaunchException if the job fails to start
     */
    Long launchImport(String inputFile) throws ImportJobLaunchException;

    /**
     * Queries the current status/progress of a previously launched import.
     *
     * @param jobExecutionId the ID returned by {@link #launchImport}
     * @return an immutable result with execution status, progress counts, and failure messages
     */
    CustomerImportResult getImportStatus(Long jobExecutionId);
}

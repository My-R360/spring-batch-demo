package com.example.spring_batch_demo.presentation.api;

import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import com.example.spring_batch_demo.application.customer.CustomerImportInputFile;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportCommand;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportEnqueueResponse;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.dto.ImportAuditReport;
import com.example.spring_batch_demo.application.customer.exceptions.ImportCommandPublishException;
import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.exceptions.InvalidCorrelationIdException;
import com.example.spring_batch_demo.application.customer.port.CustomerImportCommandPublisher;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileValidator;
import com.example.spring_batch_demo.application.customer.port.CustomerImportUseCase;
import com.example.spring_batch_demo.application.customer.port.ImportLaunchCorrelationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch")
@Slf4j
@RequiredArgsConstructor
public class BatchJobController {

    private final CustomerImportCommandPublisher customerImportCommandPublisher;
    private final CustomerImportInputFileValidator inputFileValidator;
    private final CustomerImportUseCase importUseCase;
    private final ImportLaunchCorrelationPort importLaunchCorrelationPort;

    /**
     * Accepts a customer import: publishes a command to RabbitMQ when messaging is enabled, or
     * launches the batch job in-process when messaging is disabled.
     *
     * <p>Returns 202 Accepted with {@code correlationId} and {@code status} ({@code QUEUED} or
     * {@code STARTED}). When {@code jobExecutionId} is non-null, poll {@code GET .../status} using it.
     * When status is {@code QUEUED}, poll {@code GET .../by-correlation/.../job} until {@code jobExecutionId}
     * appears.</p>
     *
     * <p>{@code inputFile} is required (non-blank): a Spring {@link org.springframework.core.io.Resource}
     * location string. Missing or blank values yield 400.</p>
     */
    @PostMapping("/customer/import")
    public ResponseEntity<CustomerImportEnqueueResponse> importCustomers(
            @RequestParam(name = "inputFile", required = false) String inputFile
    ) throws ImportJobLaunchException, ImportCommandPublishException {
        log.info("Import API called. inputFile={}", inputFile);

        String resolvedInput = CustomerImportInputFile.requireInputFileLocation(inputFile);
        inputFileValidator.validateAvailable(resolvedInput);
        String correlationId = UUID.randomUUID().toString();
        CustomerImportCommand command = CustomerImportCommand.of(correlationId, resolvedInput);
        CustomerImportEnqueueResponse body = customerImportCommandPublisher.publish(command);
        log.info(
                "Import command accepted. correlationId={} status={} jobExecutionId={}",
                body.correlationId(),
                body.status(),
                body.jobExecutionId()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    /**
     * Resolves {@code jobExecutionId} for a {@code correlationId} returned from POST import (RabbitMQ path).
     */
    @GetMapping("/customer/import/by-correlation/{correlationId}/job")
    public ResponseEntity<Map<String, Long>> getJobExecutionIdByCorrelation(
            @PathVariable String correlationId
    ) {
        requireUuidCorrelationId(correlationId);
        OptionalLong jobExecutionId = importLaunchCorrelationPort.findJobExecutionId(correlationId.trim());
        if (jobExecutionId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("jobExecutionId", jobExecutionId.getAsLong()));
    }

    /**
     * Returns the current status and progress of a previously launched import job.
     *
     * <p>HTTP mapping: {@code 404} if the execution id is unknown; {@code 500} when batch
     * {@code status} is {@code FAILED} (body is still {@link CustomerImportResult} so clients
     * keep counters and {@code failures}); {@code 200} for all other known states including
     * {@code COMPLETED} with a non-empty {@code failures} list (warnings vs hard failure).</p>
     *
     * <p>Also includes {@code filterCount} (policy-filtered rows) and a small {@code rejectedSample}
     * from persisted audit rows; use {@code GET .../report} for the full paginated list.</p>
     */
    @GetMapping("/customer/import/{jobExecutionId}/status")
    public ResponseEntity<CustomerImportResult> getImportStatus(
            @PathVariable Long jobExecutionId
    ) {
        CustomerImportResult result = importUseCase.getImportStatus(jobExecutionId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        if ("FAILED".equalsIgnoreCase(result.status())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Returns persisted per-row import audit (parse skips and policy filters) for a job execution.
     *
     * <p>HTTP mapping matches status: {@code 404} if unknown; {@code 500} when batch status is
     * {@code FAILED}; {@code 200} otherwise (including partial audit while the job is still running).</p>
     *
     * @param limit  max rows (default 50, capped server-side)
     * @param offset rows to skip for pagination (default 0)
     */
    @GetMapping("/customer/import/{jobExecutionId}/report")
    public ResponseEntity<ImportAuditReport> getImportAuditReport(
            @PathVariable Long jobExecutionId,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset
    ) {
        ImportAuditReport report = importUseCase.getImportAuditReport(jobExecutionId, limit, offset);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        if ("FAILED".equalsIgnoreCase(report.jobStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(report);
        }
        return ResponseEntity.ok(report);
    }

    private static void requireUuidCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new InvalidCorrelationIdException("correlationId must be a non-blank UUID");
        }
        try {
            UUID.fromString(correlationId.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidCorrelationIdException("correlationId must be a valid UUID");
        }
    }
}

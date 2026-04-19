package com.example.spring_batch_demo.presentation.api;

import java.util.Map;

import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.port.CustomerImportUseCase;
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

    private final CustomerImportUseCase importUseCase;

    /**
     * Launches the customer import job asynchronously.
     *
     * <p>Returns 202 Accepted immediately with the {@code jobExecutionId}.
     * Callers can poll {@code GET .../status} to track progress.</p>
     *
     * <p>{@code inputFile} is required (non-blank): a Spring {@link org.springframework.core.io.Resource}
     * location string. Missing or blank values yield 400.</p>
     */
    @PostMapping("/customer/import")
    public ResponseEntity<Map<String, Object>> importCustomers(
            @RequestParam(name = "inputFile", required = false) String inputFile
    ) throws ImportJobLaunchException {
        log.info("Import API called. inputFile={}", inputFile);

        Long jobExecutionId = importUseCase.launchImport(inputFile);
        log.info("Import job launched. jobExecutionId={}", jobExecutionId);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("jobExecutionId", jobExecutionId));
    }

    /**
     * Returns the current status and progress of a previously launched import job.
     *
     * <p>HTTP mapping: {@code 404} if the execution id is unknown; {@code 500} when batch
     * {@code status} is {@code FAILED} (body is still {@link CustomerImportResult} so clients
     * keep counters and {@code failures}); {@code 200} for all other known states including
     * {@code COMPLETED} with a non-empty {@code failures} list (warnings vs hard failure).</p>
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
}

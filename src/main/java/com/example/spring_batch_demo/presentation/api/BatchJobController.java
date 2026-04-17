package com.example.spring_batch_demo.presentation.api;

import java.util.Map;

import com.example.spring_batch_demo.application.customer.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.CustomerImportUseCase;
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
     */
    @PostMapping("/customer/import")
    public ResponseEntity<Map<String, Object>> importCustomers(
            @RequestParam(name = "inputFile", required = false) String inputFile
    ) throws Exception {
        log.info("Import API called. inputFile={}", inputFile);

        Long jobExecutionId = importUseCase.launchImport(inputFile);
        log.info("Import job launched. jobExecutionId={}", jobExecutionId);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("jobExecutionId", jobExecutionId));
    }

    /**
     * Returns the current status and progress of a previously launched import job.
     */
    @GetMapping("/customer/import/{jobExecutionId}/status")
    public ResponseEntity<CustomerImportResult> getImportStatus(
            @PathVariable Long jobExecutionId
    ) {
        CustomerImportResult result = importUseCase.getImportStatus(jobExecutionId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}

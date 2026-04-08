package com.example.spring_batch_demo.presentation.api;

import java.util.Optional;

import com.example.spring_batch_demo.application.customer.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.CustomerImportUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
     * Triggers the customer import job.
     *
     * <p>This is the outermost "presentation" entrypoint. It validates/normalizes request parameters
     * and delegates the actual work to the application use-case.</p>
     *
     * @param inputFile optional Spring {@code Resource} location. Examples:
     *                 {@code classpath:customers.csv}, {@code classpath:customers-01.csv},
     *                 {@code file:/Users/you/customers.csv}
     */
    @PostMapping("/customer/import")
    public ResponseEntity<String> importCustomers(
            @RequestParam(name = "inputFile", required = false) String inputFile
    ) throws Exception {
        String resolvedInput = Optional.ofNullable(inputFile).filter(s -> !s.isBlank()).orElse("classpath:customers.csv");
        log.info("Import API called. inputFile={}", resolvedInput);

        CustomerImportResult result = importUseCase.importCustomers(resolvedInput);

        String message = "jobExecutionId=" + result.jobExecutionId()
                + " status=" + result.status()
                + (result.failures().isEmpty() ? "" : " failures=" + String.join(" | ", result.failures()));

        if (!result.failures().isEmpty() && "FAILED".equalsIgnoreCase(result.status())) {
            log.error("Import job failed. {}", message);
            return ResponseEntity.internalServerError().body(message);
        }

        log.info("Import job finished. {}", message);
        return ResponseEntity.ok(message);
    }
}


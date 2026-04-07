package com.example.spring_batch_demo.controller;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.Optional;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/batch")
public class BatchJobController {

    private static final Logger log = LoggerFactory.getLogger(BatchJobController.class);

    private final JobLauncher jobLauncher;
    private final Job customerJob;

    public BatchJobController(JobLauncher jobLauncher, Job customerJob) {
        this.jobLauncher = jobLauncher;
        this.customerJob = customerJob;
    }

    @PostMapping("/customer/import")
    public ResponseEntity<String> importCustomers(
            @RequestParam(name = "inputFile", required = false) String inputFile
    ) throws Exception {
        String resolvedInput = Optional.ofNullable(inputFile).filter(s -> !s.isBlank()).orElse("classpath:customers.csv");

        JobParameters params = new JobParametersBuilder()
                .addString("inputFile", resolvedInput)
                .addLong("run.at", Instant.now().toEpochMilli())
                .toJobParameters();

        log.info("Launching job={} with inputFile={}", customerJob.getName(), resolvedInput);
        JobExecution execution = jobLauncher.run(customerJob, params);

        String failures = execution.getAllFailureExceptions().stream()
                .map(ex -> ex.getClass().getSimpleName() + ": " + ex.getMessage())
                .collect(Collectors.joining(" | "));

        String message = "jobExecutionId=" + execution.getId()
                + " status=" + execution.getStatus()
                + (failures.isBlank() ? "" : " failures=" + failures);

        if (execution.getStatus() == BatchStatus.FAILED) {
            log.error("Job failed. {}", message);
            return ResponseEntity.internalServerError().body(message);
        }

        log.info("Job finished. {}", message);
        return ResponseEntity.ok(message);
    }
}


package com.example.spring_batch_demo.controller;

import java.time.Instant;
import java.util.Optional;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch")
public class BatchJobController {

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
        JobParameters params = new JobParametersBuilder()
                .addString("inputFile", Optional.ofNullable(inputFile).orElse("classpath:customers.csv"))
                .addLong("run.at", Instant.now().toEpochMilli())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(customerJob, params);
        return ResponseEntity.ok("Started jobExecutionId=" + execution.getId() + " status=" + execution.getStatus());
    }
}


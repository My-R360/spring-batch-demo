package com.example.spring_batch_demo.infrastructure.batch;

import java.time.Instant;
import java.util.List;

import com.example.spring_batch_demo.application.customer.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.CustomerImportUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SpringBatchCustomerImportUseCase implements CustomerImportUseCase {

    private final JobLauncher jobLauncher;
    private final Job customerJob;
    
    public SpringBatchCustomerImportUseCase(
            JobLauncher jobLauncher,
            @Qualifier("customerJob") Job customerJob
    ) {
        this.jobLauncher = jobLauncher;
        this.customerJob = customerJob;
    }

    @Override
    public CustomerImportResult importCustomers(String inputFile) throws Exception {
        String resolvedInput = (inputFile == null || inputFile.isBlank())
                ? "classpath:customers.csv"
                : inputFile.trim();

        log.info("Launching Spring Batch job={} inputFile={}", customerJob.getName(), resolvedInput);

        JobParameters params = new JobParametersBuilder()
                .addString("inputFile", resolvedInput)
                .addLong("run.at", Instant.now().toEpochMilli())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(customerJob, params);
        List<String> failures = execution.getAllFailureExceptions().stream()
                .map(ex -> ex.getClass().getSimpleName() + ": " + ex.getMessage())
                .toList();

        return new CustomerImportResult(execution.getId(), execution.getStatus().toString(), failures);
    }
}


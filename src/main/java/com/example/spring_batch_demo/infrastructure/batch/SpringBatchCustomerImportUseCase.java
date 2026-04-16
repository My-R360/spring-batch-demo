package com.example.spring_batch_demo.infrastructure.batch;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.example.spring_batch_demo.application.customer.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.CustomerImportUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SpringBatchCustomerImportUseCase implements CustomerImportUseCase {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job customerJob;

    public SpringBatchCustomerImportUseCase(
            @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
            JobExplorer jobExplorer,
            @Qualifier("customerJob") Job customerJob
    ) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.customerJob = customerJob;
    }

    @Override
    public Long launchImport(String inputFile) throws Exception {
        String resolvedInput = (inputFile == null || inputFile.isBlank())
                ? "classpath:customers.csv"
                : inputFile.trim();

        log.info("Launching Spring Batch job={} inputFile={}", customerJob.getName(), resolvedInput);

        JobParameters params = new JobParametersBuilder()
                .addString("inputFile", resolvedInput)
                .addLong("run.at", Instant.now().toEpochMilli())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(customerJob, params);
        log.info("Job launched: jobExecutionId={} status={}", execution.getId(), execution.getStatus());
        return execution.getId();
    }

    @Override
    public CustomerImportResult getImportStatus(Long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        if (execution == null) {
            return null;
        }

        List<String> failures = resolveFailureMessages(execution);

        Collection<StepExecution> steps = execution.getStepExecutions();
        long readCount = 0, writeCount = 0, skipCount = 0;
        for (StepExecution step : steps) {
            readCount += step.getReadCount();
            writeCount += step.getWriteCount();
            skipCount += step.getSkipCount();
        }

        return new CustomerImportResult(
                execution.getId(),
                execution.getStatus().toString(),
                failures,
                readCount,
                writeCount,
                skipCount
        );
    }

    /**
     * Failure exceptions on {@link JobExecution} are not persisted; {@link JobExplorer} reloads
     * executions from the database, so {@link JobExecution#getAllFailureExceptions()} is usually
     * empty after a restart or when polling another process. Exit descriptions on the job and
     * failed steps are persisted and are the reliable source for API consumers.
     */
    private static List<String> resolveFailureMessages(JobExecution execution) {
        Set<String> messages = new LinkedHashSet<>();

        ExitStatus jobExit = execution.getExitStatus();
        if (jobExit != null) {
            addNonBlank(messages, jobExit.getExitDescription());
        }

        for (StepExecution step : execution.getStepExecutions()) {
            if (step.getStatus() != BatchStatus.FAILED) {
                continue;
            }
            ExitStatus stepExit = step.getExitStatus();
            if (stepExit == null) {
                continue;
            }
            String stepDesc = stepExit.getExitDescription();
            if (stepDesc == null || stepDesc.isBlank()) {
                continue;
            }
            addNonBlank(messages, step.getStepName() + ": " + stepDesc.trim());
        }

        if (messages.isEmpty()) {
            for (Throwable ex : execution.getAllFailureExceptions()) {
                addNonBlank(messages, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }

        return List.copyOf(messages);
    }

    private static void addNonBlank(Set<String> target, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            target.add(trimmed);
        }
    }
}

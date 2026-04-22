package com.example.spring_batch_demo.infrastructure.adapter.batch;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.example.spring_batch_demo.application.customer.CustomerImportInputFile;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.dto.ImportAuditReport;
import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileStagingPort;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileValidator;
import com.example.spring_batch_demo.application.customer.port.CustomerImportUseCase;
import com.example.spring_batch_demo.application.customer.port.ImportAuditPort;
import com.example.spring_batch_demo.domain.importaudit.RejectedRow;
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

    private static final int STATUS_REJECTED_SAMPLE = 10;
    private static final int REPORT_LIMIT_MAX = 500;

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job customerJob;
    private final ImportAuditPort importAuditPort;
    private final CustomerImportInputFileValidator inputFileValidator;
    private final CustomerImportInputFileStagingPort inputFileStagingPort;

    public SpringBatchCustomerImportUseCase(
            @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
            JobExplorer jobExplorer,
            @Qualifier("customerJob") Job customerJob,
            ImportAuditPort importAuditPort,
            CustomerImportInputFileValidator inputFileValidator,
            CustomerImportInputFileStagingPort inputFileStagingPort
    ) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.customerJob = customerJob;
        this.importAuditPort = importAuditPort;
        this.inputFileValidator = inputFileValidator;
        this.inputFileStagingPort = inputFileStagingPort;
    }

    @Override
    public Long launchImport(String inputFile) throws ImportJobLaunchException {
        String resolvedInput = CustomerImportInputFile.requireInputFileLocation(inputFile);
        String stagedInput = inputFileStagingPort.stageForImport(resolvedInput, UUID.randomUUID().toString());
        inputFileValidator.validateAvailable(stagedInput);

        log.info("Launching Spring Batch job={} inputFile={}", customerJob.getName(), stagedInput);

        JobParameters params = new JobParametersBuilder()
                .addString("inputFile", stagedInput)
                .addLong("run.at", Instant.now().toEpochMilli())
                .toJobParameters();

        try {
            JobExecution execution = jobLauncher.run(customerJob, params);
            log.info("Job launched: jobExecutionId={} status={}", execution.getId(), execution.getStatus());
            return execution.getId();
        } catch (Exception e) {
            throw new ImportJobLaunchException(e.getMessage() != null ? e.getMessage() : "Job launch failed", e);
        }
    }

    @Override
    public CustomerImportResult getImportStatus(Long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        if (execution == null) {
            return null;
        }

        List<String> failures = resolveFailureMessages(execution);

        Collection<StepExecution> steps = execution.getStepExecutions();
        long readCount = 0;
        long writeCount = 0;
        long skipCount = 0;
        long filterCount = 0;
        for (StepExecution step : steps) {
            readCount += step.getReadCount();
            writeCount += step.getWriteCount();
            skipCount += step.getSkipCount();
            filterCount += step.getFilterCount();
        }

        List<RejectedRow> rejectedSample = importAuditPort.loadRows(execution.getId(), STATUS_REJECTED_SAMPLE, 0);

        return new CustomerImportResult(
                execution.getId(),
                execution.getStatus().toString(),
                failures,
                readCount,
                writeCount,
                skipCount,
                filterCount,
                rejectedSample
        );
    }

    @Override
    public ImportAuditReport getImportAuditReport(Long jobExecutionId, int limit, int offset) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        if (execution == null) {
            return null;
        }
        int safeLimit = Math.clamp(limit, 1, REPORT_LIMIT_MAX);
        int safeOffset = Math.max(0, offset);
        long total = importAuditPort.countRejected(jobExecutionId);
        List<RejectedRow> rows = importAuditPort.loadRows(jobExecutionId, safeLimit, safeOffset);
        return new ImportAuditReport(
                execution.getId(),
                execution.getStatus().toString(),
                total,
                rows
        );
    }

    /**
     * Failure exceptions on {@link JobExecution} are not persisted; {@link JobExplorer} reloads
     * executions from the database, so {@link JobExecution#getAllFailureExceptions()} is usually
     * empty after a restart or when polling another process. Exit descriptions on the job and
     * failed steps are persisted and are the reliable source for API consumers.
     * The job-level exit description is only used when the job {@link BatchStatus} is
     * {@link BatchStatus#FAILED}; other statuses may still carry a custom exit description
     * (for example after a stop) which must not be reported as a failure.
     */
    private static List<String> resolveFailureMessages(JobExecution execution) {
        Set<String> messages = new LinkedHashSet<>();

        ExitStatus jobExit = execution.getExitStatus();
        if (jobExit != null && execution.getStatus() == BatchStatus.FAILED) {
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

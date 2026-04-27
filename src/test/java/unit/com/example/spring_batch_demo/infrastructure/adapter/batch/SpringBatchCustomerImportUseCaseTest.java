package com.example.spring_batch_demo.infrastructure.adapter.batch;

import java.util.List;

import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.dto.ImportAuditReport;
import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.exceptions.MissingInputFileException;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileStagingPort;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileValidator;
import com.example.spring_batch_demo.application.customer.port.ImportAuditPort;
import com.example.spring_batch_demo.domain.importaudit.ImportRejectionCategory;
import com.example.spring_batch_demo.domain.importaudit.RejectedRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringBatchCustomerImportUseCaseTest {

    private final JobLauncher jobLauncher = mock(JobLauncher.class);
    private final JobExplorer jobExplorer = mock(JobExplorer.class);
    private final Job job = mock(Job.class);
    private final ImportAuditPort importAuditPort = mock(ImportAuditPort.class);
    private final CustomerImportInputFileValidator inputFileValidator = mock(CustomerImportInputFileValidator.class);
    private final CustomerImportInputFileStagingPort inputFileStagingPort = mock(CustomerImportInputFileStagingPort.class);
    private final SpringBatchCustomerImportUseCase useCase =
            new SpringBatchCustomerImportUseCase(
                    jobLauncher,
                    jobExplorer,
                    job,
                    importAuditPort,
                    inputFileValidator,
                    inputFileStagingPort
            );

    @BeforeEach
    void stageInputFileByDefault() {
        when(inputFileStagingPort.stageForImport(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void launchImportRejectsNullInputFile() {
        assertThrows(MissingInputFileException.class, () -> useCase.launchImport(null));
    }

    @Test
    void launchImportRejectsBlankInputFile() {
        assertThrows(MissingInputFileException.class, () -> useCase.launchImport("   "));
    }

    @Test
    void launchImportReturnsJobExecutionId() throws Exception {
        JobExecution execution = mock(JobExecution.class);
        when(job.getName()).thenReturn("customerJob");
        when(jobLauncher.run(eq(job), any())).thenReturn(execution);
        when(execution.getId()).thenReturn(13L);
        when(execution.getStatus()).thenReturn(BatchStatus.STARTING);

        Long id = useCase.launchImport("classpath:customers-01.csv");

        assertEquals(13L, id);
        verify(inputFileStagingPort).stageForImport(eq("classpath:customers-01.csv"), any());
        verify(inputFileValidator).validateAvailable("classpath:customers-01.csv");
    }

    @Test
    void launchImportWrapsLauncherFailures() throws Exception {
        when(job.getName()).thenReturn("customerJob");
        when(jobLauncher.run(eq(job), any())).thenThrow(new RuntimeException("launch failed"));

        ImportJobLaunchException ex = assertThrows(
                ImportJobLaunchException.class,
                () -> useCase.launchImport("classpath:customers-01.csv"));
        assertTrue(ex.getMessage().contains("launch failed"));
    }

    @Test
    void getImportStatusReturnsNullWhenNotFound() {
        when(jobExplorer.getJobExecution(999L)).thenReturn(null);

        assertNull(useCase.getImportStatus(999L));
    }

    @Test
    void getImportStatusReturnsCompletedResultWithCounts() {
        JobExecution execution = mock(JobExecution.class);
        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getReadCount()).thenReturn(20L);
        when(stepExecution.getWriteCount()).thenReturn(18L);
        when(stepExecution.getSkipCount()).thenReturn(2L);
        when(stepExecution.getFilterCount()).thenReturn(1L);

        when(jobExplorer.getJobExecution(50L)).thenReturn(execution);
        when(execution.getId()).thenReturn(50L);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(execution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);
        when(execution.getStepExecutions()).thenReturn(List.of(stepExecution));
        when(importAuditPort.loadRows(50L, 10, 0)).thenReturn(List.of());

        CustomerImportResult result = useCase.getImportStatus(50L);

        assertNotNull(result);
        assertEquals(50L, result.jobExecutionId());
        assertEquals("COMPLETED", result.status());
        assertTrue(result.failures().isEmpty());
        assertEquals(20L, result.readCount());
        assertEquals(18L, result.writeCount());
        assertEquals(2L, result.skipCount());
        assertEquals(1L, result.filterCount());
        assertTrue(result.rejectedSample().isEmpty());
        verify(importAuditPort).loadRows(50L, 10, 0);
    }

    @Test
    void getImportStatusReturnsFailuresWhenJobFailed() {
        JobExecution execution = mock(JobExecution.class);

        when(jobExplorer.getJobExecution(60L)).thenReturn(execution);
        when(execution.getId()).thenReturn(60L);
        when(execution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(execution.getExitStatus()).thenReturn(new ExitStatus("FAILED", "java.lang.RuntimeException: db down"));
        when(execution.getStepExecutions()).thenReturn(List.of());
        when(importAuditPort.loadRows(60L, 10, 0)).thenReturn(List.of());

        CustomerImportResult result = useCase.getImportStatus(60L);

        assertNotNull(result);
        assertEquals("FAILED", result.status());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).contains("db down"));
    }

    @Test
    void getImportStatusIgnoresJobExitDescriptionWhenJobDidNotFail() {
        JobExecution execution = mock(JobExecution.class);
        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(stepExecution.getReadCount()).thenReturn(1L);
        when(stepExecution.getWriteCount()).thenReturn(1L);
        when(stepExecution.getSkipCount()).thenReturn(0L);
        when(stepExecution.getFilterCount()).thenReturn(0L);

        when(jobExplorer.getJobExecution(70L)).thenReturn(execution);
        when(execution.getId()).thenReturn(70L);
        when(execution.getStatus()).thenReturn(BatchStatus.STOPPED);
        when(execution.getExitStatus()).thenReturn(new ExitStatus("STOPPED", "operator requested stop"));
        when(execution.getStepExecutions()).thenReturn(List.of(stepExecution));
        when(importAuditPort.loadRows(70L, 10, 0)).thenReturn(List.of());

        CustomerImportResult result = useCase.getImportStatus(70L);

        assertNotNull(result);
        assertEquals("STOPPED", result.status());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void getImportStatusIgnoresCustomCompletedExitDescription() {
        JobExecution execution = mock(JobExecution.class);
        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(stepExecution.getReadCount()).thenReturn(1L);
        when(stepExecution.getWriteCount()).thenReturn(1L);
        when(stepExecution.getSkipCount()).thenReturn(0L);
        when(stepExecution.getFilterCount()).thenReturn(0L);

        when(jobExplorer.getJobExecution(71L)).thenReturn(execution);
        when(execution.getId()).thenReturn(71L);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(execution.getExitStatus()).thenReturn(new ExitStatus("COMPLETED", "custom completion note"));
        when(execution.getStepExecutions()).thenReturn(List.of(stepExecution));
        when(importAuditPort.loadRows(71L, 10, 0)).thenReturn(List.of());

        CustomerImportResult result = useCase.getImportStatus(71L);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void getImportAuditReportReturnsNullWhenExecutionMissing() {
        when(jobExplorer.getJobExecution(999L)).thenReturn(null);

        assertNull(useCase.getImportAuditReport(999L, 10, 0));
    }

    @Test
    void getImportAuditReportClampsLimitAndQueriesPort() {
        JobExecution execution = mock(JobExecution.class);
        when(jobExplorer.getJobExecution(50L)).thenReturn(execution);
        when(execution.getId()).thenReturn(50L);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        List<RejectedRow> rows = List.of(
                new RejectedRow(ImportRejectionCategory.POLICY_FILTER, 2L, "bad", "2", "Bob", "x")
        );
        when(importAuditPort.countRejected(50L)).thenReturn(99L);
        when(importAuditPort.loadRows(50L, 500, 10)).thenReturn(rows);

        ImportAuditReport report = useCase.getImportAuditReport(50L, 10_000, 10);

        assertNotNull(report);
        assertEquals(50L, report.jobExecutionId());
        assertEquals("COMPLETED", report.jobStatus());
        assertEquals(99L, report.totalRejectedRows());
        assertEquals(1, report.rows().size());
        verify(importAuditPort).loadRows(50L, 500, 10);
    }
}

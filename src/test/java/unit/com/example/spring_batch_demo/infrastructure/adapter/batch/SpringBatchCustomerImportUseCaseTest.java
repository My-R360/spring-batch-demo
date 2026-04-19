package com.example.spring_batch_demo.infrastructure.adapter.batch;

import java.util.List;

import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.exceptions.MissingInputFileException;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;
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
    private final SpringBatchCustomerImportUseCase useCase =
            new SpringBatchCustomerImportUseCase(jobLauncher, jobExplorer, job);

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

        when(jobExplorer.getJobExecution(50L)).thenReturn(execution);
        when(execution.getId()).thenReturn(50L);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(execution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);
        when(execution.getStepExecutions()).thenReturn(List.of(stepExecution));

        CustomerImportResult result = useCase.getImportStatus(50L);

        assertNotNull(result);
        assertEquals(50L, result.jobExecutionId());
        assertEquals("COMPLETED", result.status());
        assertTrue(result.failures().isEmpty());
        assertEquals(20L, result.readCount());
        assertEquals(18L, result.writeCount());
        assertEquals(2L, result.skipCount());
    }

    @Test
    void getImportStatusReturnsFailuresWhenJobFailed() {
        JobExecution execution = mock(JobExecution.class);

        when(jobExplorer.getJobExecution(60L)).thenReturn(execution);
        when(execution.getId()).thenReturn(60L);
        when(execution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(execution.getExitStatus()).thenReturn(new ExitStatus("FAILED", "java.lang.RuntimeException: db down"));
        when(execution.getStepExecutions()).thenReturn(List.of());

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

        when(jobExplorer.getJobExecution(70L)).thenReturn(execution);
        when(execution.getId()).thenReturn(70L);
        when(execution.getStatus()).thenReturn(BatchStatus.STOPPED);
        when(execution.getExitStatus()).thenReturn(new ExitStatus("STOPPED", "operator requested stop"));
        when(execution.getStepExecutions()).thenReturn(List.of(stepExecution));

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

        when(jobExplorer.getJobExecution(71L)).thenReturn(execution);
        when(execution.getId()).thenReturn(71L);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(execution.getExitStatus()).thenReturn(new ExitStatus("COMPLETED", "custom completion note"));
        when(execution.getStepExecutions()).thenReturn(List.of(stepExecution));

        CustomerImportResult result = useCase.getImportStatus(71L);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
        assertTrue(result.failures().isEmpty());
    }
}

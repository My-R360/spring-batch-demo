package com.example.spring_batch_demo.infrastructure.batch;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

import com.example.spring_batch_demo.application.customer.CustomerImportResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpringBatchCustomerImportUseCaseTest {

    private final JobLauncher jobLauncher = mock(JobLauncher.class);
    private final JobExplorer jobExplorer = mock(JobExplorer.class);
    private final Job job = mock(Job.class);
    private final SpringBatchCustomerImportUseCase useCase =
            new SpringBatchCustomerImportUseCase(jobLauncher, jobExplorer, job);

    @Test
    void launchImportUsesDefaultClasspathWhenInputIsNull() throws Exception {
        JobExecution execution = mock(JobExecution.class);
        when(job.getName()).thenReturn("customerJob");
        when(jobLauncher.run(eq(job), any())).thenReturn(execution);
        when(execution.getId()).thenReturn(11L);
        when(execution.getStatus()).thenReturn(BatchStatus.STARTING);

        Long id = useCase.launchImport(null);

        assertEquals(11L, id);
        verify(jobLauncher).run(eq(job), any());
    }

    @Test
    void launchImportUsesDefaultClasspathWhenInputIsBlank() throws Exception {
        JobExecution execution = mock(JobExecution.class);
        when(job.getName()).thenReturn("customerJob");
        when(jobLauncher.run(eq(job), any())).thenReturn(execution);
        when(execution.getId()).thenReturn(12L);
        when(execution.getStatus()).thenReturn(BatchStatus.STARTING);

        Long id = useCase.launchImport("   ");

        assertEquals(12L, id);
        verify(jobLauncher).run(eq(job), any());
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
}

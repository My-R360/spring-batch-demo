package com.example.spring_batch_demo.infrastructure.batch;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class JobCompletionListenerTest {

    private final JobCompletionListener listener = new JobCompletionListener();

    @Test
    void beforeJobDoesNotThrow() {
        JobExecution execution = mock(JobExecution.class);
        JobInstance jobInstance = new JobInstance(1L, "customerJob");
        when(execution.getJobInstance()).thenReturn(jobInstance);
        when(execution.getId()).thenReturn(1L);
        when(execution.getJobParameters()).thenReturn(new JobParameters());

        assertDoesNotThrow(() -> listener.beforeJob(execution));
    }

    @Test
    void afterJobLogsStepCountsForSuccessfulJob() {
        JobExecution ok = mock(JobExecution.class);
        when(ok.getJobInstance()).thenReturn(new JobInstance(1L, "customerJob"));
        when(ok.getId()).thenReturn(3L);
        when(ok.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(ok.getAllFailureExceptions()).thenReturn(List.of());

        StepExecution step = mock(StepExecution.class);
        when(step.getStepName()).thenReturn("customerStep");
        when(step.getReadCount()).thenReturn(10L);
        when(step.getWriteCount()).thenReturn(8L);
        when(step.getSkipCount()).thenReturn(2L);
        when(step.getRollbackCount()).thenReturn(0L);
        when(step.getCommitCount()).thenReturn(1L);
        when(step.getFilterCount()).thenReturn(0L);
        when(ok.getStepExecutions()).thenReturn(List.of(step));

        assertDoesNotThrow(() -> listener.afterJob(ok));
    }

    @Test
    void afterJobLogsStepCountsAndFailuresForFailedJob() {
        JobExecution failed = mock(JobExecution.class);
        when(failed.getJobInstance()).thenReturn(new JobInstance(1L, "customerJob"));
        when(failed.getId()).thenReturn(2L);
        when(failed.getStatus()).thenReturn(BatchStatus.FAILED);
        when(failed.getAllFailureExceptions()).thenReturn(List.of(new RuntimeException("x")));

        StepExecution step = mock(StepExecution.class);
        when(step.getStepName()).thenReturn("customerStep");
        when(step.getReadCount()).thenReturn(5L);
        when(step.getWriteCount()).thenReturn(3L);
        when(step.getSkipCount()).thenReturn(1L);
        when(step.getRollbackCount()).thenReturn(1L);
        when(step.getCommitCount()).thenReturn(0L);
        when(step.getFilterCount()).thenReturn(1L);
        when(failed.getStepExecutions()).thenReturn(List.of(step));

        assertDoesNotThrow(() -> listener.afterJob(failed));
    }
}

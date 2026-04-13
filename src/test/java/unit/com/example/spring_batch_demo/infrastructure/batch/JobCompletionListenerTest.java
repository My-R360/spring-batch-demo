package com.example.spring_batch_demo.infrastructure.batch;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;

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
    void afterJobHandlesFailuresAndSuccess() {
        JobExecution failed = mock(JobExecution.class);
        when(failed.getJobInstance()).thenReturn(new JobInstance(1L, "customerJob"));
        when(failed.getId()).thenReturn(2L);
        when(failed.getStatus()).thenReturn(BatchStatus.FAILED);
        when(failed.getAllFailureExceptions()).thenReturn(List.of(new RuntimeException("x")));

        JobExecution ok = mock(JobExecution.class);
        when(ok.getJobInstance()).thenReturn(new JobInstance(1L, "customerJob"));
        when(ok.getId()).thenReturn(3L);
        when(ok.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(ok.getAllFailureExceptions()).thenReturn(List.of());

        assertDoesNotThrow(() -> listener.afterJob(failed));
        assertDoesNotThrow(() -> listener.afterJob(ok));
    }
}

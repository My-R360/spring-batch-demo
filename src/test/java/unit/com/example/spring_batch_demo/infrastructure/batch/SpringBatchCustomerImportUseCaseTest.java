package com.example.spring_batch_demo.infrastructure.batch;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;

import com.example.spring_batch_demo.application.customer.CustomerImportResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpringBatchCustomerImportUseCaseTest {

    @Test
    void importCustomersUsesDefaultClasspathWhenInputIsNull() throws Exception {
        JobLauncher jobLauncher = mock(JobLauncher.class);
        Job job = mock(Job.class);
        JobExecution execution = mock(JobExecution.class);

        when(job.getName()).thenReturn("customerJob");
        when(jobLauncher.run(eq(job), any())).thenReturn(execution);
        when(execution.getId()).thenReturn(11L);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(execution.getAllFailureExceptions()).thenReturn(List.of());

        SpringBatchCustomerImportUseCase useCase = new SpringBatchCustomerImportUseCase(jobLauncher, job);
        CustomerImportResult result = useCase.importCustomers(null);

        assertEquals(11L, result.jobExecutionId());
        assertEquals("COMPLETED", result.status());
        assertTrue(result.failures().isEmpty());
        verify(jobLauncher).run(eq(job), any());
    }

    @Test
    void importCustomersReturnsFailureMessages() throws Exception {
        JobLauncher jobLauncher = mock(JobLauncher.class);
        Job job = mock(Job.class);
        JobExecution execution = mock(JobExecution.class);

        RuntimeException ex = new RuntimeException("db down");
        when(job.getName()).thenReturn("customerJob");
        when(jobLauncher.run(eq(job), any())).thenReturn(execution);
        when(execution.getId()).thenReturn(12L);
        when(execution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(execution.getAllFailureExceptions()).thenReturn(List.of(ex));

        SpringBatchCustomerImportUseCase useCase = new SpringBatchCustomerImportUseCase(jobLauncher, job);
        CustomerImportResult result = useCase.importCustomers("classpath:customers-01.csv");

        assertEquals("FAILED", result.status());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).contains("RuntimeException"));
    }

    @Test
    void importCustomersUsesDefaultClasspathWhenInputIsBlank() throws Exception {
        JobLauncher jobLauncher = mock(JobLauncher.class);
        Job job = mock(Job.class);
        JobExecution execution = mock(JobExecution.class);

        when(job.getName()).thenReturn("customerJob");
        when(jobLauncher.run(eq(job), any())).thenReturn(execution);
        when(execution.getId()).thenReturn(13L);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(execution.getAllFailureExceptions()).thenReturn(List.of());

        SpringBatchCustomerImportUseCase useCase = new SpringBatchCustomerImportUseCase(jobLauncher, job);
        CustomerImportResult result = useCase.importCustomers("   ");

        assertEquals("COMPLETED", result.status());
        verify(jobLauncher).run(eq(job), any());
    }
}

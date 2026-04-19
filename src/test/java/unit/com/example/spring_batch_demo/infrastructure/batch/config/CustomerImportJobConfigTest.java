package com.example.spring_batch_demo.infrastructure.batch.config;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.spring_batch_demo.domain.customer.Customer;
import com.example.spring_batch_demo.infrastructure.adapter.batch.JobCompletionListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class CustomerImportJobConfigTest {

    @SuppressWarnings("unchecked")
    @Test
    void customerJobAndStepBeansAreCreated() {
        JobRepository jobRepository = mock(JobRepository.class);
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        FlatFileItemReader<Customer> reader = mock(FlatFileItemReader.class);
        ItemProcessor<Customer, Customer> processor = mock(ItemProcessor.class);
        ItemWriter<Customer> writer = mock(ItemWriter.class);
        JobCompletionListener listener = new JobCompletionListener();

        CustomerImportJobConfig config = new CustomerImportJobConfig(
                jobRepository, tx, reader, processor, writer, listener
        );

        Job job = config.customerJob();
        Step step = config.customerStep();

        assertNotNull(job);
        assertNotNull(step);
        assertEquals("customerJob", job.getName());
        assertEquals("customerStep", step.getName());
    }
}

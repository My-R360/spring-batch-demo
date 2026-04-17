package com.example.spring_batch_demo.infrastructure.batch;

import com.example.spring_batch_demo.domain.customer.Customer;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CustomerImportJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    private final FlatFileItemReader<Customer> reader;
    private final ItemProcessor<Customer, Customer> processor;
    private final ItemWriter<Customer> writer;
    private final JobCompletionListener listener;

    public CustomerImportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<Customer> reader,
            ItemProcessor<Customer, Customer> processor,
            ItemWriter<Customer> writer,
            JobCompletionListener listener
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.reader = reader;
        this.processor = processor;
        this.writer = writer;
        this.listener = listener;
    }

    /**
     * Spring Batch {@link Job} definition for importing customers.
     *
     * <p>The job name ("customerJob") is part of Spring Batch identity, so keeping it stable avoids
     * confusing job-instance behavior across refactors.</p>
     */
    @Bean
    public Job customerJob() {
        return new JobBuilder("customerJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(customerStep())
                .build();
    }

    /**
     * Fault-tolerant step: retries transient DB errors with exponential backoff,
     * skips malformed CSV rows up to a configurable limit.
     */
    @Bean
    public Step customerStep() {
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(8000L);

        return new StepBuilder("customerStep", jobRepository)
                .<Customer, Customer>chunk(10, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .backOffPolicy(backOff)
                .skip(FlatFileParseException.class)
                .skipLimit(100)
                .build();
    }
}

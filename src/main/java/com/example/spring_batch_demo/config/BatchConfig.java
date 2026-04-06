package com.example.spring_batch_demo.config;

import com.example.spring_batch_demo.listener.JobCompletionListener;
import com.example.spring_batch_demo.model.Customer;
import com.example.spring_batch_demo.processor.CustomerProcessor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    private final FlatFileItemReader<Customer> reader;
    private final CustomerProcessor processor;
    private final ItemWriter<Customer> writer;
    private final JobCompletionListener listener;

    public BatchConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<Customer> reader,
            CustomerProcessor processor,
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

    @Bean
    public Job job() {
        return new JobBuilder("customerJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(step())
                .build();
    }

    @Bean
    public Step step() {
        return new StepBuilder("customerStep", jobRepository)
                .<Customer, Customer>chunk(10, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
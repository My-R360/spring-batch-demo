package com.example.spring_batch_demo.infrastructure.batch.config;

import com.example.spring_batch_demo.application.customer.CustomerImportInputFile;
import com.example.spring_batch_demo.domain.customer.Customer;
import com.example.spring_batch_demo.infrastructure.adapter.resource.CustomerImportResourceResolver;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class CustomerCsvItemReaderConfig {

    /**
     * Creates a CSV reader for the import job.
     *
     * <p>This bean is {@code @StepScope} so it can read the {@code inputFile} job parameter at
     * runtime (each job execution can point at a different file).</p>
     *
     * <p>The {@code inputFile} value is treated as a Spring {@link org.springframework.core.io.Resource}
     * location string (e.g. {@code classpath:customers.csv} or {@code file:/...}). It must be set by
     * {@code launchImport} (non-blank).</p>
     *
     * <p>Resource resolution is shared with the API validator. For {@code classpath:…} locations,
     * it uses the application class loader so imports launched from a Rabbit listener (then
     * {@link org.springframework.batch.core.launch.support.TaskExecutorJobLauncher}) still resolve on
     * a {@code batch-*} worker thread.</p>
     */
    @Bean
    @StepScope
    public FlatFileItemReader<Customer> customerReader(
            ResourceLoader resourceLoader,
            @Value("#{jobParameters['inputFile']}") String inputFile
    ) {
        String location = CustomerImportInputFile.requireJobParameterInputFile(inputFile);

        Resource resource = CustomerImportResourceResolver.resolve(resourceLoader, location);

        FieldSetMapper<Customer> mapper = fieldSet -> new Customer(
                fieldSet.readLong("id"),
                fieldSet.readString("name"),
                fieldSet.readString("email")
        );

        return new FlatFileItemReaderBuilder<Customer>()
                .name("customerReader")
                .resource(resource)
                .delimited()
                .names("id", "name", "email")
                .fieldSetMapper(mapper)
                .build();
    }
}

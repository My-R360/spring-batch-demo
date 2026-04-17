package com.example.spring_batch_demo.infrastructure.batch;

import com.example.spring_batch_demo.application.customer.CustomerImportDefaults;
import com.example.spring_batch_demo.domain.customer.Customer;
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
     * location string (e.g. {@link CustomerImportDefaults#DEFAULT_INPUT_RESOURCE_LOCATION}
     * or {@code file:/...}).</p>
     */
    @Bean
    @StepScope
    public FlatFileItemReader<Customer> customerReader(
            ResourceLoader resourceLoader,
            @Value("#{jobParameters['inputFile']}") String inputFile
    ) {
        String location = CustomerImportDefaults.resolveInputFileLocation(inputFile);

        Resource resource = resourceLoader.getResource(location);

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


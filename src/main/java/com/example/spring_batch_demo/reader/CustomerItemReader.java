package com.example.spring_batch_demo.reader;

import com.example.spring_batch_demo.model.Customer;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class CustomerItemReader {

    @Bean
    @StepScope
    public FlatFileItemReader<Customer> reader(
            ResourceLoader resourceLoader,
            @Value("#{jobParameters['inputFile']}") String inputFile
    ) {
        String location = (inputFile == null || inputFile.isBlank())
                ? "classpath:customers.csv"
                : inputFile.trim();

        Resource resource = resourceLoader.getResource(location);

        return new FlatFileItemReaderBuilder<Customer>()
                .name("customerReader")
                .resource(resource)
                .delimited()
                .names("id", "name", "email")
                .targetType(Customer.class)
                .build();
    }
}

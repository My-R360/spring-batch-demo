package com.example.spring_batch_demo.infrastructure.config.batch;

import com.example.spring_batch_demo.application.customer.port.ImportAuditPort;
import com.example.spring_batch_demo.domain.customer.Customer;
import com.example.spring_batch_demo.infrastructure.adapter.batch.CustomerImportAuditStepListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerImportAuditListenerConfig {

    @Bean
    @StepScope
    public CustomerImportAuditStepListener customerImportAuditStepListener(
            FlatFileItemReader<Customer> customerReader,
            ImportAuditPort importAuditPort
    ) {
        return new CustomerImportAuditStepListener(customerReader, importAuditPort);
    }
}

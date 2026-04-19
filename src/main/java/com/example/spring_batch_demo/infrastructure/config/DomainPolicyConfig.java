package com.example.spring_batch_demo.infrastructure.config;

import com.example.spring_batch_demo.domain.customer.policy.CustomerImportPolicy;
import com.example.spring_batch_demo.domain.customer.policy.EmailAndNameCustomerImportPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainPolicyConfig {

    /**
     * Wires the domain import policy into the Spring container.
     *
     * <p>The policy itself stays in the domain layer (no Spring annotations). Infrastructure wires
     * it as a bean so batch adapters can depend on the interface.</p>
     */
    @Bean
    public CustomerImportPolicy customerImportPolicy() {
        return new EmailAndNameCustomerImportPolicy();
    }
}


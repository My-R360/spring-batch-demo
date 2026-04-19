package com.example.spring_batch_demo.infrastructure.config;

import org.junit.jupiter.api.Test;

import com.example.spring_batch_demo.domain.customer.policy.CustomerImportPolicy;
import com.example.spring_batch_demo.domain.customer.policy.EmailAndNameCustomerImportPolicy;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DomainPolicyConfigTest {

    @Test
    void customerImportPolicyBeanIsExpectedType() {
        DomainPolicyConfig config = new DomainPolicyConfig();
        CustomerImportPolicy policy = config.customerImportPolicy();

        assertNotNull(policy);
        assertInstanceOf(EmailAndNameCustomerImportPolicy.class, policy);
    }
}

package com.example.spring_batch_demo.infrastructure.adapter.persistence;

import java.util.List;

import com.example.spring_batch_demo.application.customer.port.CustomerUpsertPort;
import com.example.spring_batch_demo.domain.customer.Customer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test / smoke profiles {@code audit-it} and {@code amqp-it} avoid Oracle-specific {@code MERGE} SQL on H2
 * while still exercising the full batch pipeline (including audit listeners).
 */
@Component
@Profile("audit-it | amqp-it")
public class NoOpCustomerUpsertPortAdapter implements CustomerUpsertPort {

    @Override
    public void upsert(List<Customer> customers) {
        // intentionally empty
    }
}

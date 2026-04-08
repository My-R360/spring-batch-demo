package com.example.spring_batch_demo.application.customer.port;

import java.util.List;

import com.example.spring_batch_demo.domain.customer.Customer;

/**
 * Application port for persisting imported customers.
 *
 * <p>Defined in the application layer so infrastructure (Oracle/JDBC) can implement it while the
 * rest of the system depends only on this abstraction.</p>
 */
public interface CustomerUpsertPort {
    /**
     * Upsert (insert or update) the provided customers.
     *
     * <p>Implementations should be idempotent for the same customer IDs (e.g. Oracle MERGE).</p>
     */
    void upsert(List<Customer> customers);
}


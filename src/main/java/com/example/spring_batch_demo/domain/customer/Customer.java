package com.example.spring_batch_demo.domain.customer;

/**
 * Domain model representing a customer being imported.
 *
 * <p>This model is used throughout the batch pipeline. It intentionally has no Spring Batch / JDBC
 * dependencies so it can remain stable even if infrastructure changes.</p>
 */
public record Customer(Long id, String name, String email) {
}

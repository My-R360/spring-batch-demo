package com.example.spring_batch_demo.domain.customer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain model representing a customer being imported.
 *
 * <p>This model is used throughout the batch pipeline. It intentionally has no Spring Batch / JDBC
 * dependencies so it can remain stable even if infrastructure changes.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    private Long id;
    private String name;
    private String email;
}


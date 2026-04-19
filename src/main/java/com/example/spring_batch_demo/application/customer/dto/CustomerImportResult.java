package com.example.spring_batch_demo.application.customer.dto;

import java.util.List;

/**
 * API / polling view of a batch job — not a domain entity (see {@code domain.customer.Customer}).
 */
public record CustomerImportResult(
        Long jobExecutionId,
        String status,
        List<String> failures,
        long readCount,
        long writeCount,
        long skipCount
) {
}

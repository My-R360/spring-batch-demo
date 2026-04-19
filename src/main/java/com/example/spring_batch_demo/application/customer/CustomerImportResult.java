package com.example.spring_batch_demo.application.customer;

import java.util.List;

public record CustomerImportResult(
        Long jobExecutionId,
        String status,
        List<String> failures,
        long readCount,
        long writeCount,
        long skipCount
) {
}


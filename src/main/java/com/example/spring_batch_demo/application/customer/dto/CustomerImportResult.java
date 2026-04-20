package com.example.spring_batch_demo.application.customer.dto;

import java.util.List;

import com.example.spring_batch_demo.domain.importaudit.RejectedRow;

/**
 * API / polling view of a batch job — not a domain entity (see {@code domain.customer.Customer}).
 *
 * @param filterCount     rows accepted by the reader but filtered by import policy (not written)
 * @param rejectedSample  first audit rows for quick inspection (full list via {@code GET .../report})
 */
public record CustomerImportResult(
        Long jobExecutionId,
        String status,
        List<String> failures,
        long readCount,
        long writeCount,
        long skipCount,
        long filterCount,
        List<RejectedRow> rejectedSample
) {
}

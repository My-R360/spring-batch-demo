package com.example.spring_batch_demo.application.customer.dto;

import java.util.List;

import com.example.spring_batch_demo.domain.importaudit.RejectedRow;

/**
 * Paginated audit view for one job execution — API shape, not a domain aggregate.
 */
public record ImportAuditReport(
        Long jobExecutionId,
        String jobStatus,
        long totalRejectedRows,
        List<RejectedRow> rows
) {
}

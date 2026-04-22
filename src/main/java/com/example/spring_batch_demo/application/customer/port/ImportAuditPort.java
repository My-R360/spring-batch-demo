package com.example.spring_batch_demo.application.customer.port;

import java.util.List;

import com.example.spring_batch_demo.domain.importaudit.RejectedRow;

/**
 * Persists and reads per-row import audit data keyed by batch job execution id.
 */
public interface ImportAuditPort {

    /**
     * Inserts one rejected-row audit record. Implementations should use a separate transaction
     * boundary when needed so audit survives chunk rollbacks.
     */
    void recordRejected(long jobExecutionId, RejectedRow row);

    long countRejected(long jobExecutionId);

    /**
     * Returns up to {@code limit} rows starting at {@code offset} for the given execution (stable order by id).
     *
     * @param jobExecutionId batch execution id
     * @param limit          max rows to return (at least 1)
     * @param offset         number of rows to skip (non-negative)
     */
    List<RejectedRow> loadRows(long jobExecutionId, int limit, int offset);
}

package com.example.spring_batch_demo.domain.importaudit;

/**
 * Why a CSV row did not result in a persisted customer for this import job.
 */
public enum ImportRejectionCategory {
    /**
     * Reader raised Spring Batch {@code FlatFileParseException} (line number usually known).
     */
    PARSE_SKIP,
    /**
     * Row was skipped during read for another throwable (still counted as read skip by Spring Batch).
     */
    READ_SKIPPED,
    /**
     * Row was read and passed policy but skipped during processing (processor threw a skippable exception).
     */
    PROCESS_SKIPPED,
    /**
     * Row was skipped during write (e.g. constraint / data integrity on upsert).
     */
    WRITE_SKIPPED,
    /**
     * Row was read successfully but filtered by domain import policy (e.g. invalid email).
     */
    POLICY_FILTER
}

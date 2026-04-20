package com.example.spring_batch_demo.domain.importaudit;

/**
 * One rejected or filtered input row for an import job — not a persisted {@code Customer}.
 *
 * @param category       how the row left the happy path
 * @param lineNumber     1-based CSV line when known (parse skips); otherwise {@code null}
 * @param reason         human-readable explanation
 * @param sourceId       raw id from input when available
 * @param sourceName     raw name from input when available
 * @param sourceEmail    raw email from input when available
 */
public record RejectedRow(
        ImportRejectionCategory category,
        Long lineNumber,
        String reason,
        String sourceId,
        String sourceName,
        String sourceEmail
) {
}

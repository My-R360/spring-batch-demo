package com.example.spring_batch_demo.application.customer.dto;

/**
 * HTTP body for {@code POST /api/batch/customer/import}: work accepted for async processing.
 *
 * @param correlationId id used with {@code GET .../by-correlation/...} until {@code jobExecutionId} is known
 * @param status          {@code QUEUED} when a broker message was published; {@code STARTED} when the job
 *                        was launched in-process (messaging disabled, e.g. audit-it profile)
 * @param jobExecutionId  present when status is {@code STARTED}; {@code null} when {@code QUEUED} until the
 *                        consumer registers the mapping
 */
public record CustomerImportEnqueueResponse(String correlationId, String status, Long jobExecutionId) {
}

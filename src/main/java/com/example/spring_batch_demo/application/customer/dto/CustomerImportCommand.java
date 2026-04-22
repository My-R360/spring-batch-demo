package com.example.spring_batch_demo.application.customer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Durable command to run a customer CSV import. Published to RabbitMQ when messaging is enabled,
 * or handled in-process for profiles without a broker.
 *
 * @param correlationId client-traceable id (UUID) linking HTTP → queue → batch logs
 * @param inputFile     non-blank Spring {@link org.springframework.core.io.Resource} location
 * @param schemaVersion for forward-compatible JSON evolution
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerImportCommand(String correlationId, String inputFile, int schemaVersion) {

    public CustomerImportCommand {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must be non-blank");
        }
        if (inputFile == null || inputFile.isBlank()) {
            throw new IllegalArgumentException("inputFile must be non-blank");
        }
    }

    public static CustomerImportCommand of(String correlationId, String inputFile) {
        return new CustomerImportCommand(correlationId, inputFile.trim(), 1);
    }
}

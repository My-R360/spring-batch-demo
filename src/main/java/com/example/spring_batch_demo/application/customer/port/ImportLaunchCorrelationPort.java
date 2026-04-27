package com.example.spring_batch_demo.application.customer.port;

import java.util.OptionalLong;

/**
 * Maps HTTP {@code correlationId} to Spring Batch {@code jobExecutionId} after a successful launch.
 */
public interface ImportLaunchCorrelationPort {

    void registerLaunchedJob(String correlationId, long jobExecutionId);

    OptionalLong findJobExecutionId(String correlationId);
}

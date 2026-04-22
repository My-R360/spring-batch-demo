package com.example.spring_batch_demo.infrastructure.adapter.messaging;

import com.example.spring_batch_demo.application.customer.dto.CustomerImportCommand;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportEnqueueResponse;
import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.port.CustomerImportCommandPublisher;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileStagingPort;
import com.example.spring_batch_demo.application.customer.port.CustomerImportUseCase;
import com.example.spring_batch_demo.application.customer.port.ImportLaunchCorrelationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * When RabbitMQ is disabled, publishes by launching the batch job on the calling thread and registering correlation.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.customer-import.enabled", havingValue = "false", matchIfMissing = true)
public class DirectCustomerImportCommandPublisher implements CustomerImportCommandPublisher {

    private final CustomerImportUseCase customerImportUseCase;
    private final ImportLaunchCorrelationPort importLaunchCorrelationPort;
    private final CustomerImportInputFileStagingPort inputFileStagingPort;

    public DirectCustomerImportCommandPublisher(
            CustomerImportUseCase customerImportUseCase,
            ImportLaunchCorrelationPort importLaunchCorrelationPort,
            CustomerImportInputFileStagingPort inputFileStagingPort
    ) {
        this.customerImportUseCase = customerImportUseCase;
        this.importLaunchCorrelationPort = importLaunchCorrelationPort;
        this.inputFileStagingPort = inputFileStagingPort;
    }

    @Override
    public CustomerImportEnqueueResponse publish(CustomerImportCommand command) throws ImportJobLaunchException {
        String stagedInputFile = inputFileStagingPort.stageForImport(command.inputFile(), command.correlationId());
        long jobExecutionId = customerImportUseCase.launchImport(stagedInputFile);
        importLaunchCorrelationPort.registerLaunchedJob(command.correlationId(), jobExecutionId);
        return new CustomerImportEnqueueResponse(command.correlationId(), "STARTED", jobExecutionId);
    }
}

package com.example.spring_batch_demo.infrastructure.adapter.messaging;

import com.example.spring_batch_demo.application.customer.dto.CustomerImportCommand;
import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.port.CustomerImportUseCase;
import com.example.spring_batch_demo.application.customer.port.ImportLaunchCorrelationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link CustomerImportCommand} messages and starts the Spring Batch job.
 *
 * <p>Ack mode is {@code AUTO}: the framework acknowledges after a successful return. Message-level
 * retry and DLQ are configured on the listener container factory.</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "app.messaging.customer-import.enabled", havingValue = "true")
public class CustomerImportJobLaunchListener {

    private final CustomerImportUseCase customerImportUseCase;
    private final ImportLaunchCorrelationPort importLaunchCorrelationPort;

    public CustomerImportJobLaunchListener(
            CustomerImportUseCase customerImportUseCase,
            ImportLaunchCorrelationPort importLaunchCorrelationPort
    ) {
        this.customerImportUseCase = customerImportUseCase;
        this.importLaunchCorrelationPort = importLaunchCorrelationPort;
    }

    @RabbitListener(
            // Resolve from the declared Queue bean so defaults on CustomerImportMessagingProperties
            // work without duplicating app.messaging.customer-import.queue in Environment.
            queues = "#{@customerImportWorkQueue.name}",
            ackMode = "AUTO",
            concurrency = "1"
    )
    public void onCustomerImportCommand(CustomerImportCommand command) throws ImportJobLaunchException {
        log.info(
                "Received customer import command correlationId={} inputFile={}",
                command.correlationId(),
                command.inputFile()
        );
        long jobExecutionId = customerImportUseCase.launchImport(command.inputFile());
        importLaunchCorrelationPort.registerLaunchedJob(command.correlationId(), jobExecutionId);
        log.info(
                "Launched customer import job correlationId={} jobExecutionId={}",
                command.correlationId(),
                jobExecutionId
        );
    }
}

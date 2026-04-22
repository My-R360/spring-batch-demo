package com.example.spring_batch_demo.infrastructure.adapter.messaging;

import com.example.spring_batch_demo.application.customer.dto.CustomerImportCommand;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportEnqueueResponse;
import com.example.spring_batch_demo.application.customer.exceptions.ImportCommandPublishException;
import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.port.CustomerImportCommandPublisher;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileStagingPort;
import com.example.spring_batch_demo.infrastructure.config.messaging.CustomerImportMessagingProperties;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.customer-import.enabled", havingValue = "true")
public class AmqpCustomerImportCommandPublisher implements CustomerImportCommandPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final CustomerImportMessagingProperties customerImportMessagingProperties;
    private final CustomerImportInputFileStagingPort inputFileStagingPort;

    public AmqpCustomerImportCommandPublisher(
            RabbitTemplate rabbitTemplate,
            CustomerImportMessagingProperties customerImportMessagingProperties,
            CustomerImportInputFileStagingPort inputFileStagingPort
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.customerImportMessagingProperties = customerImportMessagingProperties;
        this.inputFileStagingPort = inputFileStagingPort;
    }

    @Override
    public CustomerImportEnqueueResponse publish(CustomerImportCommand command)
            throws ImportJobLaunchException, ImportCommandPublishException {
        try {
            String stagedInputFile = inputFileStagingPort.stageForImport(command.inputFile(), command.correlationId());
            CustomerImportCommand stagedCommand = CustomerImportCommand.of(command.correlationId(), stagedInputFile);
            rabbitTemplate.convertAndSend(
                    customerImportMessagingProperties.getExchange(),
                    customerImportMessagingProperties.getRoutingKey(),
                    stagedCommand
            );
            return new CustomerImportEnqueueResponse(command.correlationId(), "QUEUED", null);
        } catch (AmqpException e) {
            throw new ImportCommandPublishException("Failed to publish import command to RabbitMQ", e);
        }
    }
}

package com.example.spring_batch_demo.infrastructure.adapter.messaging;

import com.example.spring_batch_demo.application.customer.dto.CustomerImportCommand;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportEnqueueResponse;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileStagingPort;
import com.example.spring_batch_demo.infrastructure.config.messaging.CustomerImportMessagingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmqpCustomerImportCommandPublisherTest {

    @Test
    void publishesStagedInputFileLocation() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        CustomerImportInputFileStagingPort inputFileStagingPort = mock(CustomerImportInputFileStagingPort.class);
        CustomerImportMessagingProperties properties = new CustomerImportMessagingProperties();
        AmqpCustomerImportCommandPublisher publisher = new AmqpCustomerImportCommandPublisher(
                rabbitTemplate,
                properties,
                inputFileStagingPort
        );
        when(inputFileStagingPort.stageForImport("file:/tmp/customers.csv", "550e8400-e29b-41d4-a716-446655440000"))
                .thenReturn("classpath:customer-imports/550e8400-e29b-41d4-a716-446655440000-customers.csv");

        CustomerImportEnqueueResponse response = publisher.publish(
                CustomerImportCommand.of("550e8400-e29b-41d4-a716-446655440000", "file:/tmp/customers.csv")
        );

        ArgumentCaptor<CustomerImportCommand> commandCaptor = ArgumentCaptor.forClass(CustomerImportCommand.class);
        verify(rabbitTemplate).convertAndSend(
                eq("customer.import.commands"),
                eq("customer.import.command"),
                commandCaptor.capture()
        );
        assertEquals("QUEUED", response.status());
        assertEquals(
                "classpath:customer-imports/550e8400-e29b-41d4-a716-446655440000-customers.csv",
                commandCaptor.getValue().inputFile()
        );
    }
}

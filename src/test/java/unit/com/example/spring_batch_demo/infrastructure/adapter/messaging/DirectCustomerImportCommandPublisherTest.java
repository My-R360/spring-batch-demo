package com.example.spring_batch_demo.infrastructure.adapter.messaging;

import com.example.spring_batch_demo.application.customer.dto.CustomerImportCommand;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportEnqueueResponse;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileStagingPort;
import com.example.spring_batch_demo.application.customer.port.CustomerImportUseCase;
import com.example.spring_batch_demo.application.customer.port.ImportLaunchCorrelationPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectCustomerImportCommandPublisherTest {

    @Test
    void launchesJobWithStagedInputFileLocation() throws Exception {
        CustomerImportUseCase customerImportUseCase = mock(CustomerImportUseCase.class);
        ImportLaunchCorrelationPort importLaunchCorrelationPort = mock(ImportLaunchCorrelationPort.class);
        CustomerImportInputFileStagingPort inputFileStagingPort = mock(CustomerImportInputFileStagingPort.class);
        DirectCustomerImportCommandPublisher publisher = new DirectCustomerImportCommandPublisher(
                customerImportUseCase,
                importLaunchCorrelationPort,
                inputFileStagingPort
        );
        when(inputFileStagingPort.stageForImport("file:/tmp/customers.csv", "550e8400-e29b-41d4-a716-446655440000"))
                .thenReturn("classpath:customer-imports/550e8400-e29b-41d4-a716-446655440000-customers.csv");
        when(customerImportUseCase.launchImport(
                "classpath:customer-imports/550e8400-e29b-41d4-a716-446655440000-customers.csv"
        )).thenReturn(42L);

        CustomerImportEnqueueResponse response = publisher.publish(
                CustomerImportCommand.of("550e8400-e29b-41d4-a716-446655440000", "file:/tmp/customers.csv")
        );

        assertEquals("STARTED", response.status());
        assertEquals(42L, response.jobExecutionId());
        verify(customerImportUseCase).launchImport(
                "classpath:customer-imports/550e8400-e29b-41d4-a716-446655440000-customers.csv"
        );
        verify(importLaunchCorrelationPort).registerLaunchedJob("550e8400-e29b-41d4-a716-446655440000", 42L);
    }
}

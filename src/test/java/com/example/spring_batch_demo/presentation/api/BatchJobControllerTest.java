package com.example.spring_batch_demo.presentation.api;

import java.util.List;

import com.example.spring_batch_demo.application.customer.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.CustomerImportUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchJobControllerTest {

    @Test
    void importCustomersReturnsInternalServerErrorWhenStatusIsFailedEvenWithoutFailures() throws Exception {
        CustomerImportUseCase importUseCase = mock(CustomerImportUseCase.class);
        BatchJobController controller = new BatchJobController(importUseCase);
        when(importUseCase.importCustomers("classpath:customers.csv"))
                .thenReturn(new CustomerImportResult(101L, "FAILED", List.of()));

        ResponseEntity<String> response = controller.importCustomers(null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void importCustomersReturnsOkWhenStatusIsCompletedEvenWithFailureMessages() throws Exception {
        CustomerImportUseCase importUseCase = mock(CustomerImportUseCase.class);
        BatchJobController controller = new BatchJobController(importUseCase);
        when(importUseCase.importCustomers("classpath:customers.csv"))
                .thenReturn(new CustomerImportResult(102L, "COMPLETED", List.of("row 5 invalid")));

        ResponseEntity<String> response = controller.importCustomers(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}

package com.example.spring_batch_demo.presentation.api;

import java.util.List;
import java.util.Map;

import com.example.spring_batch_demo.application.customer.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.CustomerImportUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchJobControllerTest {

    private final CustomerImportUseCase importUseCase = mock(CustomerImportUseCase.class);
    private final BatchJobController controller = new BatchJobController(importUseCase);

    @Test
    void importCustomersReturnsAcceptedWithJobExecutionId() throws Exception {
        when(importUseCase.launchImport(isNull())).thenReturn(101L);

        ResponseEntity<Map<String, Object>> response = controller.importCustomers(null);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(101L, response.getBody().get("jobExecutionId"));
        verify(importUseCase).launchImport(isNull());
    }

    @Test
    void importCustomersUsesProvidedInputFile() throws Exception {
        when(importUseCase.launchImport("classpath:customers-01.csv")).thenReturn(102L);

        ResponseEntity<Map<String, Object>> response = controller.importCustomers("classpath:customers-01.csv");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(importUseCase).launchImport("classpath:customers-01.csv");
    }

    @Test
    void importCustomersUsesDefaultWhenInputFileIsBlank() throws Exception {
        when(importUseCase.launchImport("   ")).thenReturn(103L);

        ResponseEntity<Map<String, Object>> response = controller.importCustomers("   ");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(importUseCase).launchImport("   ");
    }

    @Test
    void getImportStatusReturnsResultWhenFound() {
        CustomerImportResult result = new CustomerImportResult(50L, "COMPLETED", List.of(), 10L, 8L, 2L);
        when(importUseCase.getImportStatus(50L)).thenReturn(result);

        ResponseEntity<CustomerImportResult> response = controller.getImportStatus(50L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(result, response.getBody());
    }

    @Test
    void getImportStatusReturnsNotFoundWhenMissing() {
        when(importUseCase.getImportStatus(999L)).thenReturn(null);

        ResponseEntity<CustomerImportResult> response = controller.getImportStatus(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getImportStatusReturnsInProgressJob() {
        CustomerImportResult result = new CustomerImportResult(60L, "STARTED", List.of(), 5L, 3L, 0L);
        when(importUseCase.getImportStatus(60L)).thenReturn(result);

        ResponseEntity<CustomerImportResult> response = controller.getImportStatus(60L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("STARTED", response.getBody().status());
    }

    @Test
    void getImportStatusReturnsServerErrorWhenBatchFailed() {
        CustomerImportResult result = new CustomerImportResult(70L, "FAILED", List.of(), 0L, 0L, 0L);
        when(importUseCase.getImportStatus(70L)).thenReturn(result);

        ResponseEntity<CustomerImportResult> response = controller.getImportStatus(70L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(result, response.getBody());
    }

    @Test
    void getImportStatusReturnsServerErrorForFailedIgnoringCase() {
        CustomerImportResult result = new CustomerImportResult(71L, "failed", List.of("step died"), 1L, 0L, 1L);
        when(importUseCase.getImportStatus(71L)).thenReturn(result);

        ResponseEntity<CustomerImportResult> response = controller.getImportStatus(71L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("failed", response.getBody().status());
    }
}

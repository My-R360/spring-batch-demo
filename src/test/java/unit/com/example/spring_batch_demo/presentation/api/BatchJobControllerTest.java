package com.example.spring_batch_demo.presentation.api;

import java.util.List;
import java.util.Map;

import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.exceptions.MissingInputFileException;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.dto.ImportAuditReport;
import com.example.spring_batch_demo.application.customer.port.CustomerImportUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchJobControllerTest {

    private final CustomerImportUseCase importUseCase = mock(CustomerImportUseCase.class);
    private final BatchJobController controller = new BatchJobController(importUseCase);

    @Test
    void importCustomersRejectsMissingInputFile() throws ImportJobLaunchException {
        when(importUseCase.launchImport(null)).thenThrow(MissingInputFileException.forQueryParameter());

        assertThrows(MissingInputFileException.class, () -> controller.importCustomers(null));

        verify(importUseCase).launchImport(null);
    }

    @Test
    void importCustomersUsesProvidedInputFile() throws ImportJobLaunchException {
        when(importUseCase.launchImport("classpath:customers-01.csv")).thenReturn(102L);

        ResponseEntity<Map<String, Object>> response = controller.importCustomers("classpath:customers-01.csv");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(importUseCase).launchImport("classpath:customers-01.csv");
    }

    @Test
    void importCustomersRejectsBlankInputFile() throws ImportJobLaunchException {
        when(importUseCase.launchImport("   ")).thenThrow(MissingInputFileException.forQueryParameter());

        assertThrows(MissingInputFileException.class, () -> controller.importCustomers("   "));

        verify(importUseCase).launchImport("   ");
    }

    @Test
    void getImportStatusReturnsResultWhenFound() {
        CustomerImportResult result = new CustomerImportResult(50L, "COMPLETED", List.of(), 10L, 8L, 2L, 0L, List.of());
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
        CustomerImportResult result = new CustomerImportResult(60L, "STARTED", List.of(), 5L, 3L, 0L, 0L, List.of());
        when(importUseCase.getImportStatus(60L)).thenReturn(result);

        ResponseEntity<CustomerImportResult> response = controller.getImportStatus(60L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("STARTED", response.getBody().status());
    }

    @Test
    void getImportStatusReturnsServerErrorWhenBatchFailed() {
        CustomerImportResult result = new CustomerImportResult(70L, "FAILED", List.of(), 0L, 0L, 0L, 0L, List.of());
        when(importUseCase.getImportStatus(70L)).thenReturn(result);

        ResponseEntity<CustomerImportResult> response = controller.getImportStatus(70L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(result, response.getBody());
    }

    @Test
    void getImportStatusReturnsServerErrorForFailedIgnoringCase() {
        CustomerImportResult result = new CustomerImportResult(71L, "failed", List.of("step died"), 1L, 0L, 1L, 0L, List.of());
        when(importUseCase.getImportStatus(71L)).thenReturn(result);

        ResponseEntity<CustomerImportResult> response = controller.getImportStatus(71L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("failed", response.getBody().status());
    }

    @Test
    void getImportAuditReportReturnsOkWhenFound() {
        ImportAuditReport report = new ImportAuditReport(12L, "COMPLETED", 0L, List.of());
        when(importUseCase.getImportAuditReport(12L, 50, 0)).thenReturn(report);

        ResponseEntity<ImportAuditReport> response = controller.getImportAuditReport(12L, 50, 0);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(report, response.getBody());
    }

    @Test
    void getImportAuditReportReturnsNotFoundWhenMissing() {
        when(importUseCase.getImportAuditReport(9L, 50, 0)).thenReturn(null);

        ResponseEntity<ImportAuditReport> response = controller.getImportAuditReport(9L, 50, 0);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getImportAuditReportReturns500WhenJobFailed() {
        ImportAuditReport report = new ImportAuditReport(13L, "FAILED", 1L, List.of());
        when(importUseCase.getImportAuditReport(13L, 50, 0)).thenReturn(report);

        ResponseEntity<ImportAuditReport> response = controller.getImportAuditReport(13L, 50, 0);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(report, response.getBody());
    }
}

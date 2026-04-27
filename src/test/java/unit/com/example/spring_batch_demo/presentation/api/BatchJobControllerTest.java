package com.example.spring_batch_demo.presentation.api;

import java.util.List;
import java.util.OptionalLong;

import com.example.spring_batch_demo.application.customer.dto.CustomerImportEnqueueResponse;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.dto.ImportAuditReport;
import com.example.spring_batch_demo.application.customer.exceptions.ImportCommandPublishException;
import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.exceptions.InvalidCorrelationIdException;
import com.example.spring_batch_demo.application.customer.exceptions.InvalidInputFileResourceException;
import com.example.spring_batch_demo.application.customer.exceptions.MissingInputFileException;
import com.example.spring_batch_demo.application.customer.port.CustomerImportCommandPublisher;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileValidator;
import com.example.spring_batch_demo.application.customer.port.CustomerImportUseCase;
import com.example.spring_batch_demo.application.customer.port.ImportLaunchCorrelationPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchJobControllerTest {

    private final CustomerImportCommandPublisher customerImportCommandPublisher = mock(CustomerImportCommandPublisher.class);
    private final CustomerImportInputFileValidator inputFileValidator = mock(CustomerImportInputFileValidator.class);
    private final CustomerImportUseCase importUseCase = mock(CustomerImportUseCase.class);
    private final ImportLaunchCorrelationPort importLaunchCorrelationPort = mock(ImportLaunchCorrelationPort.class);
    private final BatchJobController controller = new BatchJobController(
            customerImportCommandPublisher,
            inputFileValidator,
            importUseCase,
            importLaunchCorrelationPort
    );

    @Test
    void importCustomersRejectsMissingInputFile() {
        assertThrows(MissingInputFileException.class, () -> controller.importCustomers(null));
        verifyNoInteractions(customerImportCommandPublisher);
        verifyNoInteractions(inputFileValidator);
    }

    @Test
    void importCustomersUsesProvidedInputFile() throws Exception {
        when(customerImportCommandPublisher.publish(any())).thenReturn(
                new CustomerImportEnqueueResponse("c1", "QUEUED", null)
        );

        ResponseEntity<com.example.spring_batch_demo.application.customer.dto.CustomerImportEnqueueResponse> response =
                controller.importCustomers("classpath:customers-01.csv");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("c1", response.getBody().correlationId());
        assertEquals("QUEUED", response.getBody().status());
        verify(inputFileValidator).validateAvailable("classpath:customers-01.csv");
        verify(customerImportCommandPublisher).publish(any());
    }

    @Test
    void importCustomersRejectsBlankInputFile() {
        assertThrows(MissingInputFileException.class, () -> controller.importCustomers("   "));
        verifyNoInteractions(customerImportCommandPublisher);
        verifyNoInteractions(inputFileValidator);
    }

    @Test
    void importCustomersRejectsUnavailableInputFileBeforePublishing() {
        doThrow(new InvalidInputFileResourceException("missing"))
                .when(inputFileValidator).validateAvailable(eq("file:/missing.csv"));

        assertThrows(InvalidInputFileResourceException.class, () -> controller.importCustomers("file:/missing.csv"));
        verifyNoInteractions(customerImportCommandPublisher);
    }

    @Test
    void importCustomersPropagatesPublishFailure() throws Exception {
        when(customerImportCommandPublisher.publish(any()))
                .thenThrow(new ImportCommandPublishException("broker down", null));

        assertThrows(ImportCommandPublishException.class, () -> controller.importCustomers("classpath:customers-01.csv"));
    }

    @Test
    void importCustomersPropagatesLaunchFailureFromPublisher() throws Exception {
        when(customerImportCommandPublisher.publish(any()))
                .thenThrow(new ImportJobLaunchException("bad start", null));

        assertThrows(ImportJobLaunchException.class, () -> controller.importCustomers("classpath:customers-01.csv"));
    }

    @Test
    void getJobByCorrelationReturnsJobId() {
        when(importLaunchCorrelationPort.findJobExecutionId("550e8400-e29b-41d4-a716-446655440000"))
                .thenReturn(OptionalLong.of(42L));

        ResponseEntity<java.util.Map<String, Long>> response =
                controller.getJobExecutionIdByCorrelation("550e8400-e29b-41d4-a716-446655440000");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(42L, response.getBody().get("jobExecutionId").longValue());
    }

    @Test
    void getJobByCorrelationReturnsNotFound() {
        when(importLaunchCorrelationPort.findJobExecutionId("550e8400-e29b-41d4-a716-446655440001"))
                .thenReturn(OptionalLong.empty());

        ResponseEntity<java.util.Map<String, Long>> response =
                controller.getJobExecutionIdByCorrelation("550e8400-e29b-41d4-a716-446655440001");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getJobByCorrelationRejectsInvalidUuid() {
        assertThrows(InvalidCorrelationIdException.class, () -> controller.getJobExecutionIdByCorrelation("not-a-uuid"));
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

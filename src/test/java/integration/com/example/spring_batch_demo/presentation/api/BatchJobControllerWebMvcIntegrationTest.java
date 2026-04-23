package com.example.spring_batch_demo.presentation.api;

import java.util.List;
import java.util.OptionalLong;

import com.example.spring_batch_demo.application.customer.dto.CustomerImportEnqueueResponse;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.dto.ImportAuditReport;
import com.example.spring_batch_demo.application.customer.exceptions.ImportCommandPublishException;
import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.exceptions.InputFileStagingException;
import com.example.spring_batch_demo.application.customer.exceptions.InvalidInputFileResourceException;
import com.example.spring_batch_demo.application.customer.port.CustomerImportCommandPublisher;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileValidator;
import com.example.spring_batch_demo.application.customer.port.CustomerImportUseCase;
import com.example.spring_batch_demo.application.customer.port.ImportLaunchCorrelationPort;
import com.example.spring_batch_demo.presentation.api.exceptions.BatchJobApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BatchJobController.class)
@ActiveProfiles("test")
@Import(BatchJobApiExceptionHandler.class)
class BatchJobControllerWebMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerImportCommandPublisher customerImportCommandPublisher;

    @MockBean
    private CustomerImportInputFileValidator inputFileValidator;

    @MockBean
    private CustomerImportUseCase useCase;

    @MockBean
    private ImportLaunchCorrelationPort importLaunchCorrelationPort;

    @Test
    void postImportReturnsAcceptedWithQueuedPayload() throws Exception {
        when(customerImportCommandPublisher.publish(any())).thenReturn(
                new CustomerImportEnqueueResponse("550e8400-e29b-41d4-a716-446655440000", "QUEUED", null)
        );

        mockMvc.perform(post("/api/batch/customer/import").param("inputFile", "classpath:customers-01.csv"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correlationId").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void postImportReturnsAcceptedWithStartedPayloadWhenJobIdPresent() throws Exception {
        when(customerImportCommandPublisher.publish(any())).thenReturn(
                new CustomerImportEnqueueResponse("550e8400-e29b-41d4-a716-446655440001", "STARTED", 33L)
        );

        mockMvc.perform(post("/api/batch/customer/import").param("inputFile", "classpath:customers-01.csv"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobExecutionId").value(33))
                .andExpect(jsonPath("$.status").value("STARTED"));
    }

    @Test
    void postImportReturnsProblemDetailWhenLaunchFails() throws Exception {
        when(customerImportCommandPublisher.publish(any()))
                .thenThrow(new ImportJobLaunchException("bad start", null));

        mockMvc.perform(post("/api/batch/customer/import").param("inputFile", "classpath:customers-01.csv"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("bad start"))
                .andExpect(jsonPath("$.title").value("Import job launch failed"));
    }

    @Test
    void postImportReturnsServiceUnavailableWhenPublishFails() throws Exception {
        when(customerImportCommandPublisher.publish(any()))
                .thenThrow(new ImportCommandPublishException("broker down", null));

        mockMvc.perform(post("/api/batch/customer/import").param("inputFile", "classpath:customers-01.csv"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("broker down"))
                .andExpect(jsonPath("$.title").value("Import command publish failed"));
    }

    @Test
    void postImportReturnsBadRequestWhenInputFileMissing() throws Exception {
        mockMvc.perform(post("/api/batch/customer/import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing input file"));
    }

    @Test
    void postImportReturnsBadRequestForBlankInputFileWithoutCallingPublisher() throws Exception {
        mockMvc.perform(post("/api/batch/customer/import").param("inputFile", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postImportReturnsBadRequestWhenInputFileDoesNotExist() throws Exception {
        doThrow(new InvalidInputFileResourceException("Input file resource does not exist: file:/missing.csv"))
                .when(inputFileValidator).validateAvailable(eq("file:/missing.csv"));

        mockMvc.perform(post("/api/batch/customer/import").param("inputFile", "file:/missing.csv"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid input file"))
                .andExpect(jsonPath("$.detail").value("Input file resource does not exist: file:/missing.csv"));
    }

    @Test
    void postImportReturnsServerErrorWhenInputFileStagingFails() throws Exception {
        when(customerImportCommandPublisher.publish(any()))
                .thenThrow(new InputFileStagingException("Unable to stage input file locally for import: file:/data/customers.csv"));

        mockMvc.perform(post("/api/batch/customer/import").param("inputFile", "file:/data/customers.csv"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Input file staging failed"))
                .andExpect(jsonPath("$.detail").value("Unable to stage input file locally for import: file:/data/customers.csv"));
    }

    @Test
    void getJobByCorrelationReturnsOk() throws Exception {
        when(importLaunchCorrelationPort.findJobExecutionId(eq("550e8400-e29b-41d4-a716-446655440010")))
                .thenReturn(OptionalLong.of(99L));

        mockMvc.perform(get("/api/batch/customer/import/by-correlation/550e8400-e29b-41d4-a716-446655440010/job"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value(99));
    }

    @Test
    void getJobByCorrelationReturnsNotFound() throws Exception {
        when(importLaunchCorrelationPort.findJobExecutionId(eq("550e8400-e29b-41d4-a716-446655440011")))
                .thenReturn(OptionalLong.empty());

        mockMvc.perform(get("/api/batch/customer/import/by-correlation/550e8400-e29b-41d4-a716-446655440011/job"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJobByCorrelationReturnsBadRequestForInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/batch/customer/import/by-correlation/not-a-uuid/job"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid correlation id"));
    }

    @Test
    void getStatusReturnsCompletedResult() throws Exception {
        when(useCase.getImportStatus(33L))
                .thenReturn(new CustomerImportResult(33L, "COMPLETED", List.of(), 10L, 8L, 2L, 1L, List.of()));

        mockMvc.perform(get("/api/batch/customer/import/33/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value(33))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.readCount").value(10))
                .andExpect(jsonPath("$.writeCount").value(8))
                .andExpect(jsonPath("$.skipCount").value(2))
                .andExpect(jsonPath("$.filterCount").value(1));
    }

    @Test
    void getStatusReturnsNotFoundForUnknownId() throws Exception {
        when(useCase.getImportStatus(999L)).thenReturn(null);

        mockMvc.perform(get("/api/batch/customer/import/999/status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStatusReturnsInProgressJob() throws Exception {
        when(useCase.getImportStatus(50L))
                .thenReturn(new CustomerImportResult(50L, "STARTED", List.of(), 5L, 3L, 0L, 0L, List.of()));

        mockMvc.perform(get("/api/batch/customer/import/50/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andExpect(jsonPath("$.readCount").value(5));
    }

    @Test
    void getStatusReturnsInternalServerErrorWhenFailed() throws Exception {
        when(useCase.getImportStatus(70L))
                .thenReturn(new CustomerImportResult(70L, "FAILED", List.of("boom"), 0L, 0L, 0L, 0L, List.of()));

        mockMvc.perform(get("/api/batch/customer/import/70/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failures[0]").value("boom"));
    }

    @Test
    void getStatusReturnsBadRequestForNonNumericJobExecutionId() throws Exception {
        mockMvc.perform(get("/api/batch/customer/import/not-a-number/status"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReportReturnsOk() throws Exception {
        when(useCase.getImportAuditReport(eq(33L), eq(50), eq(0)))
                .thenReturn(new ImportAuditReport(33L, "COMPLETED", 2L, List.of()));

        mockMvc.perform(get("/api/batch/customer/import/33/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value(33))
                .andExpect(jsonPath("$.jobStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.totalRejectedRows").value(2));
    }

    @Test
    void getReportPassesLimitAndOffsetQueryParams() throws Exception {
        when(useCase.getImportAuditReport(eq(33L), eq(5), eq(10)))
                .thenReturn(new ImportAuditReport(33L, "COMPLETED", 0L, List.of()));

        mockMvc.perform(get("/api/batch/customer/import/33/report").param("limit", "5").param("offset", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void getReportReturnsNotFoundWhenUnknown() throws Exception {
        when(useCase.getImportAuditReport(eq(999L), eq(50), eq(0))).thenReturn(null);

        mockMvc.perform(get("/api/batch/customer/import/999/report"))
                .andExpect(status().isNotFound());
    }

    @Test
    void problemDetailReturnsJson() throws Exception {
        when(customerImportCommandPublisher.publish(any()))
                .thenThrow(new ImportJobLaunchException("x", null));

        mockMvc.perform(
                        post("/api/batch/customer/import").param("inputFile", "classpath:customers-01.csv")
                                .accept(MediaType.APPLICATION_PROBLEM_JSON)
                )
                .andExpect(status().isInternalServerError());
    }
}

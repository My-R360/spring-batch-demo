package com.example.spring_batch_demo.presentation.api;

import java.util.List;

import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.exceptions.MissingInputFileException;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.port.CustomerImportUseCase;
import com.example.spring_batch_demo.presentation.api.exceptions.BatchJobApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BatchJobController.class)
@Import(BatchJobApiExceptionHandler.class)
class BatchJobControllerWebMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerImportUseCase useCase;

    @Test
    void postImportReturnsAcceptedWithJobExecutionId() throws Exception {
        when(useCase.launchImport(eq("classpath:customers-01.csv"))).thenReturn(33L);

        mockMvc.perform(post("/api/batch/customer/import").param("inputFile", "classpath:customers-01.csv"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobExecutionId").value(33));
    }

    @Test
    void postImportReturnsProblemDetailWhenLaunchFails() throws Exception {
        when(useCase.launchImport(eq("classpath:customers-01.csv")))
                .thenThrow(new ImportJobLaunchException("bad start", null));

        mockMvc.perform(post("/api/batch/customer/import").param("inputFile", "classpath:customers-01.csv"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("bad start"))
                .andExpect(jsonPath("$.title").value("Import job launch failed"));
    }

    @Test
    void postImportReturnsBadRequestWhenUseCaseSignalsMissingInputFile() throws Exception {
        when(useCase.launchImport(isNull())).thenThrow(MissingInputFileException.forQueryParameter());

        mockMvc.perform(post("/api/batch/customer/import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing input file"));
    }

    @Test
    void getStatusReturnsCompletedResult() throws Exception {
        when(useCase.getImportStatus(33L))
                .thenReturn(new CustomerImportResult(33L, "COMPLETED", List.of(), 10L, 8L, 2L));

        mockMvc.perform(get("/api/batch/customer/import/33/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value(33))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.readCount").value(10))
                .andExpect(jsonPath("$.writeCount").value(8))
                .andExpect(jsonPath("$.skipCount").value(2));
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
                .thenReturn(new CustomerImportResult(50L, "STARTED", List.of(), 5L, 3L, 0L));

        mockMvc.perform(get("/api/batch/customer/import/50/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andExpect(jsonPath("$.readCount").value(5));
    }

    @Test
    void getStatusReturnsInternalServerErrorWhenFailed() throws Exception {
        when(useCase.getImportStatus(70L))
                .thenReturn(new CustomerImportResult(70L, "FAILED", List.of("boom"), 0L, 0L, 0L));

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
}

package com.example.spring_batch_demo.presentation.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.spring_batch_demo.application.customer.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.CustomerImportUseCase;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BatchJobController.class)
class BatchJobControllerWebMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerImportUseCase useCase;

    @Test
    void postImportReturnsOkForCompletedResult() throws Exception {
        when(useCase.importCustomers("classpath:customers.csv"))
                .thenReturn(new CustomerImportResult(33L, "COMPLETED", List.of()));

        mockMvc.perform(post("/api/batch/customer/import"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jobExecutionId=33")));
    }

    @Test
    void postImportReturnsInternalServerErrorForFailedResult() throws Exception {
        when(useCase.importCustomers("classpath:customers.csv"))
                .thenReturn(new CustomerImportResult(34L, "FAILED", List.of("db error")));

        mockMvc.perform(post("/api/batch/customer/import"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FAILED")));
    }
}

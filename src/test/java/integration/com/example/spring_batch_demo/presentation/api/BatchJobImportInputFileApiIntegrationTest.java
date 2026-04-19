package com.example.spring_batch_demo.presentation.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BatchJobImportInputFileApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postImportWithoutInputFileReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/batch/customer/import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing input file"));
    }

    @Test
    void postImportWithBlankInputFileReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/batch/customer/import").param("inputFile", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing input file"));
    }
}

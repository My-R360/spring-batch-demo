package com.example.spring_batch_demo.application.customer;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomerImportResultTest {

    @Test
    void recordFieldsAndEqualityWork() {
        CustomerImportResult r1 = new CustomerImportResult(10L, "COMPLETED", List.of());
        CustomerImportResult r2 = new CustomerImportResult(10L, "COMPLETED", List.of());

        assertEquals(10L, r1.jobExecutionId());
        assertEquals("COMPLETED", r1.status());
        assertTrue(r1.failures().isEmpty());
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertTrue(r1.toString().contains("COMPLETED"));
    }
}

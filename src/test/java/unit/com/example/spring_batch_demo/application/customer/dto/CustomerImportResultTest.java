package com.example.spring_batch_demo.application.customer.dto;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerImportResultTest {

    @Test
    void recordFieldsAndEqualityWork() {
        CustomerImportResult r1 = new CustomerImportResult(10L, "COMPLETED", List.of(), 20L, 18L, 2L, 0L);
        CustomerImportResult r2 = new CustomerImportResult(10L, "COMPLETED", List.of(), 20L, 18L, 2L, 0L);

        assertEquals(10L, r1.jobExecutionId());
        assertEquals("COMPLETED", r1.status());
        assertTrue(r1.failures().isEmpty());
        assertEquals(20L, r1.readCount());
        assertEquals(18L, r1.writeCount());
        assertEquals(2L, r1.skipCount());
        assertEquals(0L, r1.filterCount());
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertTrue(r1.toString().contains("COMPLETED"));
    }
}

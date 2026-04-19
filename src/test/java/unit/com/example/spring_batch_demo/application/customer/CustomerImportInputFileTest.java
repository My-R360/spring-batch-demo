package com.example.spring_batch_demo.application.customer;

import com.example.spring_batch_demo.application.customer.exceptions.MissingInputFileException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerImportInputFileTest {

    @Test
    void requireInputFileLocationTrimsAndReturns() {
        assertEquals("classpath:a.csv", CustomerImportInputFile.requireInputFileLocation("  classpath:a.csv  "));
    }

    @Test
    void requireInputFileLocationRejectsNull() {
        assertThrows(MissingInputFileException.class, () -> CustomerImportInputFile.requireInputFileLocation(null));
    }

    @Test
    void requireInputFileLocationRejectsBlank() {
        assertThrows(MissingInputFileException.class, () -> CustomerImportInputFile.requireInputFileLocation("   "));
    }

    @Test
    void requireJobParameterInputFileRejectsNull() {
        assertThrows(IllegalStateException.class, () -> CustomerImportInputFile.requireJobParameterInputFile(null));
    }
}

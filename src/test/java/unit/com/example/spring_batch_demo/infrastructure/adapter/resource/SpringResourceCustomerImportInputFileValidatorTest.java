package com.example.spring_batch_demo.infrastructure.adapter.resource;

import java.nio.file.Files;
import java.nio.file.Path;

import com.example.spring_batch_demo.application.customer.exceptions.InvalidInputFileResourceException;
import com.example.spring_batch_demo.application.customer.exceptions.MissingInputFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringResourceCustomerImportInputFileValidatorTest {

    private final SpringResourceCustomerImportInputFileValidator validator =
            new SpringResourceCustomerImportInputFileValidator(new DefaultResourceLoader());

    @Test
    void validatesClasspathResource() {
        assertDoesNotThrow(() -> validator.validateAvailable("classpath:customers.csv"));
    }

    @Test
    void validatesFileResource(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("customers.csv");
        Files.writeString(csv, "1,Alice,alice@example.com\n");

        assertDoesNotThrow(() -> validator.validateAvailable("file:" + csv.toAbsolutePath()));
    }

    @Test
    void validatesPlainLocalPath(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("customers.csv");
        Files.writeString(csv, "1,Alice,alice@example.com\n");

        assertDoesNotThrow(() -> validator.validateAvailable(csv.toAbsolutePath().toString()));
    }

    @Test
    void rejectsMissingFileResource() {
        assertThrows(
                InvalidInputFileResourceException.class,
                () -> validator.validateAvailable("file:/path/to/your/customers.csv")
        );
    }

    @Test
    void rejectsBlankLocation() {
        assertThrows(MissingInputFileException.class, () -> validator.validateAvailable("   "));
    }
}

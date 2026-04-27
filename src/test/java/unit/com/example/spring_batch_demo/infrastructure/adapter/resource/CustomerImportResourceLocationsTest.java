package com.example.spring_batch_demo.infrastructure.adapter.resource;

import java.nio.file.Path;

import com.example.spring_batch_demo.application.customer.exceptions.InvalidInputFileResourceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerImportResourceLocationsTest {

    @Test
    void classpathLocationsAreNotResolvedAsLocalFiles() {
        assertNull(CustomerImportResourceLocations.resolveLocalSourcePath("classpath:customers.csv"));
        assertNull(CustomerImportResourceLocations.resolveLocalSourcePath("classpath*:customers/*.csv"));
    }

    @Test
    void sharedStorageStyleLocationsAreLeftForResourceHandling() {
        assertNull(CustomerImportResourceLocations.resolveLocalSourcePath("s3://bucket/customers.csv"));
    }

    @Test
    void fileLocationsResolveToLocalPaths(@TempDir Path tempDir) {
        Path csv = tempDir.resolve("customers.csv");

        assertEquals(csv, CustomerImportResourceLocations.resolveLocalSourcePath("file:" + csv));
    }

    @Test
    void emptyFileLocationIsRejected() {
        assertThrows(
                InvalidInputFileResourceException.class,
                () -> CustomerImportResourceLocations.resolveLocalSourcePath("file:")
        );
    }
}

package com.example.spring_batch_demo.infrastructure.batch;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.core.io.DefaultResourceLoader;

import com.example.spring_batch_demo.domain.customer.Customer;

import static org.junit.jupiter.api.Assertions.*;

class CustomerCsvItemReaderConfigTest {

    private final CustomerCsvItemReaderConfig config = new CustomerCsvItemReaderConfig();

    @Test
    void customerReaderUsesDefaultClasspathWhenInputMissing() throws Exception {
        FlatFileItemReader<Customer> reader = config.customerReader(new DefaultResourceLoader(), null);
        reader.open(new org.springframework.batch.item.ExecutionContext());
        Customer first = reader.read();
        reader.close();

        assertNotNull(first);
        assertEquals(1L, first.id());
    }

    @Test
    void customerReaderReadsFromFileResource(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("x.csv");
        Files.writeString(csv, "100,Neo,neo@matrix.com\n");

        FlatFileItemReader<Customer> reader = config.customerReader(
                new DefaultResourceLoader(),
                "file:" + csv.toAbsolutePath()
        );
        reader.open(new org.springframework.batch.item.ExecutionContext());
        Customer first = reader.read();
        reader.close();

        assertNotNull(first);
        assertEquals(100L, first.id());
        assertEquals("Neo", first.name());
    }

    @Test
    void customerReaderUsesDefaultClasspathWhenInputBlank() throws Exception {
        FlatFileItemReader<Customer> reader = config.customerReader(new DefaultResourceLoader(), "   ");
        reader.open(new org.springframework.batch.item.ExecutionContext());
        Customer first = reader.read();
        reader.close();

        assertNotNull(first);
        assertEquals(1L, first.id());
    }
}

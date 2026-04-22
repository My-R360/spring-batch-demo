package com.example.spring_batch_demo.infrastructure.adapter.resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.example.spring_batch_demo.application.customer.exceptions.InvalidInputFileResourceException;
import com.example.spring_batch_demo.infrastructure.config.CustomerImportLocalStagingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalClasspathCustomerImportInputFileStagingAdapterTest {

    @Test
    void leavesClasspathResourceUnchanged() {
        LocalClasspathCustomerImportInputFileStagingAdapter stager = newStager();

        String staged = stager.stageForImport("classpath:customers.csv", "import-1");

        assertEquals("classpath:customers.csv", staged);
    }

    @Test
    void copiesFileResourceToRuntimeClasspath(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Custom Customers 01.csv");
        Files.writeString(source, "1,Alice,alice@example.com\n");
        String prefix = "customer-imports-test/" + UUID.randomUUID();
        Path classpathDirectory = Path.of("target/classes").resolve(prefix);
        LocalClasspathCustomerImportInputFileStagingAdapter stager = newStager(classpathDirectory, prefix);

        String staged = stager.stageForImport("file:" + source.toAbsolutePath(), "550e8400-e29b-41d4-a716-446655440000");

        assertTrue(staged.startsWith("classpath:" + prefix + "/550e8400-e29b-41d4-a716-446655440000-"));
        Path stagedFile = stagedFilePath(classpathDirectory, staged, prefix);
        assertTrue(Files.exists(stagedFile));
        assertEquals("1,Alice,alice@example.com\n", Files.readString(stagedFile));
    }

    @Test
    void copiesPlainLocalPathToRuntimeClasspath(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("customers.csv");
        Files.writeString(source, "2,Bob,bob@example.com\n");
        String prefix = "customer-imports-test/" + UUID.randomUUID();
        Path classpathDirectory = Path.of("target/classes").resolve(prefix);
        LocalClasspathCustomerImportInputFileStagingAdapter stager = newStager(classpathDirectory, prefix);

        String staged = stager.stageForImport(source.toAbsolutePath().toString(), "import-2");

        assertTrue(staged.startsWith("classpath:" + prefix + "/import-2-customers.csv"));
        Path stagedFile = stagedFilePath(classpathDirectory, staged, prefix);
        assertTrue(Files.exists(stagedFile));
        assertEquals("2,Bob,bob@example.com\n", Files.readString(stagedFile));
    }

    @Test
    void rejectsMissingLocalFile(@TempDir Path tempDir) {
        LocalClasspathCustomerImportInputFileStagingAdapter stager = newStager();

        assertThrows(
                InvalidInputFileResourceException.class,
                () -> stager.stageForImport("file:" + tempDir.resolve("missing.csv").toAbsolutePath(), "import-3")
        );
    }

    private static LocalClasspathCustomerImportInputFileStagingAdapter newStager() {
        return newStager(Path.of("target/classes/customer-imports"), "customer-imports");
    }

    private static LocalClasspathCustomerImportInputFileStagingAdapter newStager(
            Path classpathDirectory,
            String classpathLocationPrefix
    ) {
        CustomerImportLocalStagingProperties properties = new CustomerImportLocalStagingProperties();
        properties.setClasspathDirectory(classpathDirectory);
        properties.setClasspathLocationPrefix(classpathLocationPrefix);
        return new LocalClasspathCustomerImportInputFileStagingAdapter(properties, new DefaultResourceLoader());
    }

    private static Path stagedFilePath(Path classpathDirectory, String stagedLocation, String prefix) {
        String fileName = stagedLocation.substring(("classpath:" + prefix + "/").length());
        return Path.of("").toAbsolutePath().resolve(classpathDirectory).normalize().resolve(fileName);
    }
}

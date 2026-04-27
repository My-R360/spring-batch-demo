package com.example.spring_batch_demo.infrastructure.adapter.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

import com.example.spring_batch_demo.application.customer.CustomerImportInputFile;
import com.example.spring_batch_demo.application.customer.exceptions.InvalidInputFileResourceException;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileStagingPort;
import com.example.spring_batch_demo.infrastructure.config.CustomerImportLocalStagingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Local development staging adapter.
 *
 * <p>This copies local {@code file:} and plain-path CSV inputs into the runtime classpath so a
 * same-machine RabbitMQ consumer can read them after asynchronous hand-off. A distributed deployment
 * should replace this port with an adapter that writes to shared storage, such as S3 or another
 * location visible to every consumer.</p>
 */
@Component
@Slf4j
public class LocalClasspathCustomerImportInputFileStagingAdapter implements CustomerImportInputFileStagingPort {

    private static final String DEFAULT_CLASSPATH_LOCATION_PREFIX = "customer-imports";
    private static final Path DEFAULT_CLASSPATH_DIRECTORY = Path.of("target/classes/customer-imports");
    private static final int MAX_FILE_NAME_LENGTH = 180;
    private static final Pattern UNSAFE_FILE_NAME_CHARACTERS = Pattern.compile("[^A-Za-z0-9._-]");
    private static final Pattern REPEATED_UNDERSCORES = Pattern.compile("_+");

    private final CustomerImportLocalStagingProperties properties;
    private final ResourceLoader resourceLoader;

    public LocalClasspathCustomerImportInputFileStagingAdapter(
            CustomerImportLocalStagingProperties properties,
            ResourceLoader resourceLoader
    ) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String stageForImport(String inputFile, String importId) {
        String location = CustomerImportInputFile.requireInputFileLocation(inputFile);
        if (CustomerImportResourceLocations.isClasspathLocation(location)) {
            return location;
        }

        Path source = CustomerImportResourceLocations.resolveLocalSourcePath(location);
        if (source == null) {
            return location;
        }

        Path sourceFile = source.toAbsolutePath().normalize();
        CustomerImportResourceLocations.validateReadableRegularFile(sourceFile, location);

        Path stagingDirectory = resolveClasspathDirectory();
        String stagedFileName = buildStagedFileName(importId, sourceFile.getFileName());
        Path stagedFile = stagingDirectory.resolve(stagedFileName).normalize();
        if (!stagedFile.startsWith(stagingDirectory)) {
            throw new InvalidInputFileResourceException("Invalid staged file path for input file: " + location);
        }

        try {
            Files.createDirectories(stagingDirectory);
            Files.copy(sourceFile, stagedFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new InvalidInputFileResourceException(
                    "Unable to stage input file locally for import: " + location, ex);
        }

        String stagedLocation = CustomerImportResourceLocations.CLASSPATH_PREFIX
                + normalizeClasspathLocationPrefix() + "/" + stagedFileName;
        validateStagedClasspathResource(stagedLocation);
        log.info("Staged customer import file source={} stagedLocation={}", sourceFile, stagedLocation);
        return stagedLocation;
    }

    private Path resolveClasspathDirectory() {
        Path configuredDirectory = properties.getClasspathDirectory();
        Path directory = configuredDirectory == null ? DEFAULT_CLASSPATH_DIRECTORY : configuredDirectory;
        if (directory.isAbsolute()) {
            return directory.normalize();
        }
        return Path.of("").toAbsolutePath().resolve(directory).normalize();
    }

    private String normalizeClasspathLocationPrefix() {
        String configuredPrefix = properties.getClasspathLocationPrefix();
        String prefix = configuredPrefix == null || configuredPrefix.isBlank()
                ? DEFAULT_CLASSPATH_LOCATION_PREFIX
                : configuredPrefix.trim();
        String normalizedPrefix = prefix.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
        return normalizedPrefix.isBlank() ? DEFAULT_CLASSPATH_LOCATION_PREFIX : normalizedPrefix;
    }

    private static String buildStagedFileName(String importId, Path sourceFileName) {
        String safeImportId = sanitizeFileName(importId);
        if (safeImportId.isBlank()) {
            safeImportId = UUID.randomUUID().toString();
        }
        String originalFileName = sourceFileName == null ? "customers.csv" : sourceFileName.toString();
        String safeOriginalFileName = sanitizeFileName(originalFileName);
        if (safeOriginalFileName.isBlank()) {
            safeOriginalFileName = "customers.csv";
        }
        String fileName = safeImportId + "-" + safeOriginalFileName;
        if (fileName.length() <= MAX_FILE_NAME_LENGTH) {
            return fileName;
        }
        int allowedOriginalLength = Math.max(1, MAX_FILE_NAME_LENGTH - safeImportId.length() - 1);
        return safeImportId + "-" + safeOriginalFileName.substring(0, allowedOriginalLength);
    }

    private static String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = UNSAFE_FILE_NAME_CHARACTERS.matcher(value.trim()).replaceAll("_");
        sanitized = REPEATED_UNDERSCORES.matcher(sanitized).replaceAll("_");
        sanitized = sanitized.replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        if (".".equals(sanitized) || "..".equals(sanitized)) {
            return "";
        }
        return sanitized;
    }

    private void validateStagedClasspathResource(String stagedLocation) {
        Resource resource = CustomerImportResourceResolver.resolve(resourceLoader, stagedLocation);
        boolean available;
        try {
            available = resource.exists() && resource.isReadable();
        } catch (RuntimeException ex) {
            throw new InvalidInputFileResourceException(
                    "Unable to resolve staged input file from the runtime classpath: " + stagedLocation, ex);
        }
        if (!available) {
            throw new InvalidInputFileResourceException(
                    "Staged input file is not readable from the runtime classpath: " + stagedLocation
                            + ". Check app.customer-import.local-staging.classpath-directory.");
        }
    }
}

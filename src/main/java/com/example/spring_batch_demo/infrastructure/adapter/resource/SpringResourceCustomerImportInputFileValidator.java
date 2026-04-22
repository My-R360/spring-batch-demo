package com.example.spring_batch_demo.infrastructure.adapter.resource;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import com.example.spring_batch_demo.application.customer.CustomerImportInputFile;
import com.example.spring_batch_demo.application.customer.exceptions.InvalidInputFileResourceException;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileValidator;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class SpringResourceCustomerImportInputFileValidator implements CustomerImportInputFileValidator {

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String CLASSPATH_ALL_PREFIX = "classpath*:";
    private static final String FILE_PREFIX = "file:";
    private static final Pattern RESOURCE_PREFIX = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    private final ResourceLoader resourceLoader;

    public SpringResourceCustomerImportInputFileValidator(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void validateAvailable(String inputFile) {
        String location = CustomerImportInputFile.requireInputFileLocation(inputFile);
        Path localFile = resolveLocalSourcePath(location);
        if (localFile != null) {
            validateLocalFile(localFile.toAbsolutePath().normalize(), location);
            return;
        }

        Resource resource = CustomerImportResourceResolver.resolve(resourceLoader, location);
        if (!exists(resource, location)) {
            throw new InvalidInputFileResourceException(
                    "Input file resource does not exist: " + location + ". "
                            + "Use classpath:<name> for bundled files or file:/absolute/path/to/file.csv "
                            + "for a file readable by the application process.");
        }
        if (!isReadable(resource, location)) {
            throw new InvalidInputFileResourceException(
                    "Input file resource is not readable: " + location + ". "
                            + "Check file permissions and make sure the application process can access it.");
        }
    }

    private static Path resolveLocalSourcePath(String location) {
        if (isClasspathLocation(location)) {
            return null;
        }
        if (location.startsWith(FILE_PREFIX)) {
            return resolveFileUriPath(location);
        }
        if (RESOURCE_PREFIX.matcher(location).find()) {
            return null;
        }
        return Path.of(location);
    }

    private static boolean isClasspathLocation(String location) {
        return location.startsWith(CLASSPATH_PREFIX) || location.startsWith(CLASSPATH_ALL_PREFIX);
    }

    private static Path resolveFileUriPath(String location) {
        try {
            return Path.of(URI.create(location));
        } catch (IllegalArgumentException ex) {
            String rawPath = location.substring(FILE_PREFIX.length()).trim();
            if (rawPath.isEmpty()) {
                throw new InvalidInputFileResourceException("Input file resource does not contain a file path: " + location);
            }
            return Path.of(rawPath);
        }
    }

    private static void validateLocalFile(Path localFile, String location) {
        if (!Files.exists(localFile)) {
            throw new InvalidInputFileResourceException(
                    "Input file resource does not exist: " + location + ". "
                            + "Use classpath:<name> for bundled files or file:/absolute/path/to/file.csv "
                            + "for a file readable by the application process.");
        }
        if (!Files.isRegularFile(localFile)) {
            throw new InvalidInputFileResourceException("Input file resource is not a regular file: " + location);
        }
        if (!Files.isReadable(localFile)) {
            throw new InvalidInputFileResourceException(
                    "Input file resource is not readable: " + location + ". "
                            + "Check file permissions and make sure the application process can access it.");
        }
    }

    private static boolean exists(Resource resource, String location) {
        try {
            return resource.exists();
        } catch (RuntimeException ex) {
            throw new InvalidInputFileResourceException("Unable to resolve input file resource: " + location, ex);
        }
    }

    private static boolean isReadable(Resource resource, String location) {
        try {
            return resource.isReadable();
        } catch (RuntimeException ex) {
            throw new InvalidInputFileResourceException("Unable to check input file readability: " + location, ex);
        }
    }
}

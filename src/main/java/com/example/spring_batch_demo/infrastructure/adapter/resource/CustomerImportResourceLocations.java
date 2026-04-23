package com.example.spring_batch_demo.infrastructure.adapter.resource;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import com.example.spring_batch_demo.application.customer.exceptions.InvalidInputFileResourceException;

final class CustomerImportResourceLocations {

    static final String CLASSPATH_PREFIX = "classpath:";
    static final String CLASSPATH_ALL_PREFIX = "classpath*:";
    static final String FILE_PREFIX = "file:";

    private static final Pattern RESOURCE_PREFIX = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    private CustomerImportResourceLocations() {
    }

    static boolean isClasspathLocation(String location) {
        return location.startsWith(CLASSPATH_PREFIX) || location.startsWith(CLASSPATH_ALL_PREFIX);
    }

    static Path resolveLocalSourcePath(String location) {
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

    static Path resolveFileUriPath(String location) {
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

    static void validateReadableRegularFile(Path localFile, String location) {
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
}

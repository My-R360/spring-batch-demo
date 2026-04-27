package com.example.spring_batch_demo.infrastructure.adapter.resource;

import java.nio.file.Path;

import com.example.spring_batch_demo.application.customer.CustomerImportInputFile;
import com.example.spring_batch_demo.application.customer.exceptions.InvalidInputFileResourceException;
import com.example.spring_batch_demo.application.customer.port.CustomerImportInputFileValidator;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class SpringResourceCustomerImportInputFileValidator implements CustomerImportInputFileValidator {

    private final ResourceLoader resourceLoader;

    public SpringResourceCustomerImportInputFileValidator(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void validateAvailable(String inputFile) {
        String location = CustomerImportInputFile.requireInputFileLocation(inputFile);
        Path localFile = CustomerImportResourceLocations.resolveLocalSourcePath(location);
        if (localFile != null) {
            CustomerImportResourceLocations.validateReadableRegularFile(localFile.toAbsolutePath().normalize(), location);
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

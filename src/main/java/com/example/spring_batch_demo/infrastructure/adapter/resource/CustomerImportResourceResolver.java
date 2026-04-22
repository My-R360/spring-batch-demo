package com.example.spring_batch_demo.infrastructure.adapter.resource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Resolves customer import resource locations consistently for validation and the Batch reader.
 */
public final class CustomerImportResourceResolver {

    private CustomerImportResourceResolver() {
    }

    /**
     * Prefer an explicit application class loader for {@code classpath:} locations so resolution does
     * not depend on a worker thread's context class loader.
     */
    public static Resource resolve(ResourceLoader resourceLoader, String location) {
        String trimmed = location.trim();
        if (trimmed.startsWith("classpath:") && !trimmed.startsWith("classpath*:")) {
            String path = trimmed.substring("classpath:".length()).trim();
            return new ClassPathResource(path, CustomerImportResourceResolver.class.getClassLoader());
        }
        return resourceLoader.getResource(trimmed);
    }
}

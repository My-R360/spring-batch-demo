package com.example.spring_batch_demo.infrastructure.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.customer-import.local-staging")
public class CustomerImportLocalStagingProperties {

    /**
     * Runtime classpath directory used by local development to stage external CSV files.
     */
    private Path classpathDirectory = Path.of("target/classes/customer-imports");

    /**
     * Classpath location prefix that maps to {@link #classpathDirectory}.
     */
    private String classpathLocationPrefix = "customer-imports";

    public Path getClasspathDirectory() {
        return classpathDirectory;
    }

    public void setClasspathDirectory(Path classpathDirectory) {
        this.classpathDirectory = classpathDirectory;
    }

    public String getClasspathLocationPrefix() {
        return classpathLocationPrefix;
    }

    public void setClasspathLocationPrefix(String classpathLocationPrefix) {
        this.classpathLocationPrefix = classpathLocationPrefix;
    }
}

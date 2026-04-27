package com.example.spring_batch_demo.infrastructure.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Fails fast when the configured datasource pool cannot sustain the allowed
 * batch concurrency while still leaving JDBC headroom for status/report APIs.
 */
@Component
public class CustomerImportExecutionCapacityGuard {

    private final DataSource dataSource;
    private final CustomerImportExecutionProperties properties;

    public CustomerImportExecutionCapacityGuard(
            DataSource dataSource,
            CustomerImportExecutionProperties properties
    ) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @PostConstruct
    void validateCapacity() {
        if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
            return;
        }

        int configuredMaximum = hikariDataSource.getMaximumPoolSize();
        int requiredMinimum = properties.requiredJdbcPoolSize();
        if (configuredMaximum < requiredMinimum) {
            throw new IllegalStateException(
                    "spring.datasource.hikari.maximum-pool-size=" + configuredMaximum
                            + " is too small for customer import execution settings; need at least "
                            + requiredMinimum
                            + " to cover maxConcurrentJobs="
                            + properties.getMaxConcurrentJobs()
                            + ", estimatedMaxDbConnectionsPerJob="
                            + properties.getEstimatedMaxDbConnectionsPerJob()
                            + ", reservedDbConnections="
                            + properties.getReservedDbConnections()
            );
        }
    }
}

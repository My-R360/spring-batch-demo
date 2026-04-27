package com.example.spring_batch_demo.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerImportExecutionCapacityGuardTest {

    @Test
    void requiredJdbcPoolSizeReflectsBatchAndReservedCapacity() {
        CustomerImportExecutionProperties properties = new CustomerImportExecutionProperties();
        properties.setMaxConcurrentJobs(3);
        properties.setEstimatedMaxDbConnectionsPerJob(2);
        properties.setReservedDbConnections(4);

        assertEquals(10, properties.requiredJdbcPoolSize());
    }

    @Test
    void validateCapacityFailsWhenHikariPoolWouldBeExhaustedByConfiguration() {
        CustomerImportExecutionProperties properties = new CustomerImportExecutionProperties();
        properties.setMaxConcurrentJobs(3);
        properties.setEstimatedMaxDbConnectionsPerJob(2);
        properties.setReservedDbConnections(4);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setMaximumPoolSize(9);
        try {
            CustomerImportExecutionCapacityGuard guard =
                    new CustomerImportExecutionCapacityGuard(dataSource, properties);

            IllegalStateException thrown =
                    assertThrows(IllegalStateException.class, guard::validateCapacity);

            assertEquals(
                    "spring.datasource.hikari.maximum-pool-size=9 is too small for customer import execution settings; "
                            + "need at least 10 to cover maxConcurrentJobs=3, estimatedMaxDbConnectionsPerJob=2, "
                            + "reservedDbConnections=4",
                    thrown.getMessage()
            );
        } finally {
            dataSource.close();
        }
    }

    @Test
    void validateCapacityAllowsMatchingPoolSize() {
        CustomerImportExecutionProperties properties = new CustomerImportExecutionProperties();
        properties.setMaxConcurrentJobs(3);
        properties.setEstimatedMaxDbConnectionsPerJob(2);
        properties.setReservedDbConnections(4);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setMaximumPoolSize(10);
        try {
            CustomerImportExecutionCapacityGuard guard =
                    new CustomerImportExecutionCapacityGuard(dataSource, properties);

            assertDoesNotThrow(guard::validateCapacity);
        } finally {
            dataSource.close();
        }
    }
}

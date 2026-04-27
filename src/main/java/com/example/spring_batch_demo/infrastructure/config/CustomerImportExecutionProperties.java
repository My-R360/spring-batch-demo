package com.example.spring_batch_demo.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime guards for local customer-import throughput.
 *
 * <p>The import step can temporarily need two JDBC connections per running job:
 * one for the chunk transaction and one for REQUIRES_NEW audit writes. These
 * settings keep batch throughput below the point where status/report endpoints
 * are starved of connections.</p>
 */
@ConfigurationProperties(prefix = "app.customer-import.execution")
public class CustomerImportExecutionProperties {

    private int maxConcurrentJobs = 3;
    private int estimatedMaxDbConnectionsPerJob = 2;
    private int reservedDbConnections = 4;
    private String threadNamePrefix = "batch-";

    public int getMaxConcurrentJobs() {
        return maxConcurrentJobs;
    }

    public void setMaxConcurrentJobs(int maxConcurrentJobs) {
        this.maxConcurrentJobs = maxConcurrentJobs;
    }

    public int getEstimatedMaxDbConnectionsPerJob() {
        return estimatedMaxDbConnectionsPerJob;
    }

    public void setEstimatedMaxDbConnectionsPerJob(int estimatedMaxDbConnectionsPerJob) {
        this.estimatedMaxDbConnectionsPerJob = estimatedMaxDbConnectionsPerJob;
    }

    public int getReservedDbConnections() {
        return reservedDbConnections;
    }

    public void setReservedDbConnections(int reservedDbConnections) {
        this.reservedDbConnections = reservedDbConnections;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public int requiredJdbcPoolSize() {
        return Math.max(1, maxConcurrentJobs) * Math.max(1, estimatedMaxDbConnectionsPerJob)
                + Math.max(0, reservedDbConnections);
    }
}

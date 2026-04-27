package com.example.spring_batch_demo.infrastructure.config;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Provides a bounded async {@link JobLauncher}. Once the configured job slots are full,
 * submission blocks until a worker becomes available, so RabbitMQ keeps the backlog instead
 * of the JVM building up an unbounded queue of launched jobs.
 */
@Configuration
@EnableConfigurationProperties(CustomerImportExecutionProperties.class)
public class AsyncJobLauncherConfig {

    @Bean("asyncJobLauncher")
    public JobLauncher asyncJobLauncher(
            JobRepository jobRepository,
            CustomerImportExecutionProperties properties
    ) throws Exception {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setCorePoolSize(properties.getMaxConcurrentJobs());
        executor.setMaxPoolSize(properties.getMaxConcurrentJobs());
        executor.setQueueCapacity(0);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new BlockingSubmissionPolicy());
        executor.initialize();

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(executor);
        launcher.afterPropertiesSet();
        return launcher;
    }

    private static final class BlockingSubmissionPolicy implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                throw new java.util.concurrent.RejectedExecutionException("Batch executor is shut down");
            }
            try {
                executor.getQueue().put(runnable);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.RejectedExecutionException(
                        "Interrupted while waiting for a batch execution slot",
                        ex
                );
            }
        }
    }
}

package com.example.spring_batch_demo.infrastructure.config;

import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Provides an async {@link JobLauncher} so that {@code JobLauncher.run()} returns immediately
 * with a STARTING execution while the job runs in a background thread.
 */
@Configuration
public class AsyncJobLauncherConfig {

    @Bean("asyncJobLauncher")
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("batch-"));
        launcher.afterPropertiesSet();
        return launcher;
    }
}

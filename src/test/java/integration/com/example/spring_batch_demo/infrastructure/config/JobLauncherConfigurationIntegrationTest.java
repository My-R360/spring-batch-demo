package com.example.spring_batch_demo.infrastructure.config;

import com.example.spring_batch_demo.SpringBatchDemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@SpringBootTest(classes = SpringBatchDemoApplication.class)
@ActiveProfiles("test")
class JobLauncherConfigurationIntegrationTest {

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("jobLauncher")
    private JobLauncher defaultJobLauncher;

    @Test
    void jobLauncherUsesAsyncTaskExecutor() {
        TaskExecutorJobLauncher asyncLauncher = assertInstanceOf(TaskExecutorJobLauncher.class, jobLauncher);
        TaskExecutor asyncTaskExecutor = (TaskExecutor) ReflectionTestUtils.getField(asyncLauncher, "taskExecutor");
        ThreadPoolTaskExecutor threadPoolTaskExecutor =
                assertInstanceOf(ThreadPoolTaskExecutor.class, asyncTaskExecutor);
        assertEquals(3, threadPoolTaskExecutor.getCorePoolSize());
        assertEquals(3, threadPoolTaskExecutor.getMaxPoolSize());

        TaskExecutorJobLauncher defaultLauncher = assertInstanceOf(TaskExecutorJobLauncher.class, defaultJobLauncher);
        TaskExecutor defaultTaskExecutor = (TaskExecutor) ReflectionTestUtils.getField(defaultLauncher, "taskExecutor");
        assertInstanceOf(SyncTaskExecutor.class, defaultTaskExecutor);

        assertNotSame(defaultJobLauncher, jobLauncher);
    }
}

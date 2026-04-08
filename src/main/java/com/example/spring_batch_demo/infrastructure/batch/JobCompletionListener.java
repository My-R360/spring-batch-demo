package com.example.spring_batch_demo.infrastructure.batch;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JobCompletionListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("JOB STARTED name={} id={} params={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (!jobExecution.getAllFailureExceptions().isEmpty()) {
            log.error("JOB FINISHED name={} id={} status={} failures={}",
                    jobExecution.getJobInstance().getJobName(),
                    jobExecution.getId(),
                    jobExecution.getStatus(),
                    jobExecution.getAllFailureExceptions());
            return;
        }

        log.info("JOB FINISHED name={} id={} status={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getStatus());
    }
}


package com.example.spring_batch_demo.listener;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JobCompletionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionListener.class);

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


package com.example.spring_batch_demo.listener;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class JobCompletionListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        System.out.println("JOB STARTED");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        System.out.println("JOB FINISHED with status: " + jobExecution.getStatus());
    }
}


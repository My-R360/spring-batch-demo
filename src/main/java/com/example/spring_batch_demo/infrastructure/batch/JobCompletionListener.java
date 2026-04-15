package com.example.spring_batch_demo.infrastructure.batch;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
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
        String jobName = jobExecution.getJobInstance().getJobName();
        Long jobId = jobExecution.getId();

        for (StepExecution step : jobExecution.getStepExecutions()) {
            log.info("STEP SUMMARY job={} id={} step={} read={} written={} skipped={} "
                            + "rollbacks={} commits={} filterCount={}",
                    jobName, jobId, step.getStepName(),
                    step.getReadCount(), step.getWriteCount(), step.getSkipCount(),
                    step.getRollbackCount(), step.getCommitCount(), step.getFilterCount());
        }

        if (!jobExecution.getAllFailureExceptions().isEmpty()) {
            log.error("JOB FINISHED name={} id={} status={} failures={}",
                    jobName, jobId, jobExecution.getStatus(),
                    jobExecution.getAllFailureExceptions());
            return;
        }

        log.info("JOB FINISHED name={} id={} status={}",
                jobName, jobId, jobExecution.getStatus());
    }
}

package com.balancetrail.batch;

import com.balancetrail.service.RunStateService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/** Keeps the reconciliation_run row in step with the batch job: RUNNING, then COMPLETED or FAILED. */
@Component
public class ReconciliationJobListener implements JobExecutionListener {
  private static final Logger log = LoggerFactory.getLogger(ReconciliationJobListener.class);
  private final RunStateService runStateService;

  public ReconciliationJobListener(RunStateService runStateService) {
    this.runStateService = runStateService;
  }

  @Override
  public void beforeJob(JobExecution jobExecution) {
    UUID runId = runId(jobExecution);
    runStateService.markRunning(runId);
    log.info("Starting reconciliation run {}", runId);
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    UUID runId = runId(jobExecution);
    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
      try {
        runStateService.summarize(runId);
      } catch (RuntimeException exception) {
        runStateService.markFailed(runId, "Could not summarize results: " + exception.getMessage());
      }
    } else {
      runStateService.markFailed(runId, firstFailureMessage(jobExecution));
    }
    log.info("Finished reconciliation run {} with batch status {}", runId, jobExecution.getStatus());
  }

  private String firstFailureMessage(JobExecution jobExecution) {
    return jobExecution.getAllFailureExceptions().stream()
        .findFirst()
        .map(Throwable::getMessage)
        .orElse("Reconciliation job failed");
  }

  private UUID runId(JobExecution jobExecution) {
    return UUID.fromString(jobExecution.getJobParameters().getString("runId"));
  }
}

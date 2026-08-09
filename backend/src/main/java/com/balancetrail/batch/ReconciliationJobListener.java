package com.balancetrail.batch;

import com.balancetrail.service.RunStateService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

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
    boolean reconciliationFailed =
        jobExecution.getStatus() != BatchStatus.COMPLETED
            || jobExecution.getStepExecutions().stream()
            .anyMatch(
                step ->
                    step.getStepName().equals("reconciliationStep")
                        && step.getStatus() != BatchStatus.COMPLETED);
    if (reconciliationFailed) {
      String message =
          jobExecution.getAllFailureExceptions().stream()
              .findFirst()
              .map(Throwable::getMessage)
              .orElse("Reconciliation step failed; partial results were summarized");
      runStateService.markFailed(runId, message);
    }
    log.info(
        "Finished reconciliation run {} with batch status {} in {} ms",
        runId,
        jobExecution.getStatus(),
        jobExecution.getEndTime() == null || jobExecution.getStartTime() == null
            ? -1
            : java.time.Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime())
                .toMillis());
  }

  private UUID runId(JobExecution jobExecution) {
    return UUID.fromString(jobExecution.getJobParameters().getString("runId"));
  }
}

package com.balancetrail.batch;

import com.balancetrail.service.LedgerImportStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

/** Keeps the ledger_import row in step with the batch job: RUNNING, then COMPLETED or FAILED. */
@Component
public class LedgerImportJobListener implements JobExecutionListener {
  private static final Logger log = LoggerFactory.getLogger(LedgerImportJobListener.class);
  private final LedgerImportStateService stateService;

  public LedgerImportJobListener(LedgerImportStateService stateService) {
    this.stateService = stateService;
  }

  @Override
  public void beforeJob(JobExecution jobExecution) {
    stateService.markRunning(importId(jobExecution));
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    long importId = importId(jobExecution);
    // Even a failed import may have saved some chunks, so refresh statistics either way.
    try {
      stateService.refreshLedgerStatistics();
    } catch (RuntimeException exception) {
      log.warn("Could not analyze ledger_transaction after import {}", importId, exception);
    }
    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
      long written =
          jobExecution.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum();
      try {
        stateService.complete(importId, written);
      } catch (RuntimeException exception) {
        stateService.markFailed(importId, "Could not summarize import: " + exception.getMessage());
      }
    } else {
      String message =
          jobExecution.getAllFailureExceptions().stream()
              .findFirst()
              .map(Throwable::getMessage)
              .orElse("Ledger import failed");
      stateService.markFailed(importId, message);
    }
    log.info("Finished ledger import {} with batch status {}", importId, jobExecution.getStatus());
  }

  private long importId(JobExecution jobExecution) {
    return Long.parseLong(jobExecution.getJobParameters().getString("importId"));
  }
}

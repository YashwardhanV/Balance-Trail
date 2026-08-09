package com.balancetrail.service;

import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BatchLaunchListener {
  private static final Logger log = LoggerFactory.getLogger(BatchLaunchListener.class);
  private final TaskExecutor taskExecutor;
  private final JobLauncher jobLauncher;
  private final Job reconciliationJob;
  private final RunStateService runStateService;

  public BatchLaunchListener(
      @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor,
      JobLauncher jobLauncher,
      @Qualifier("reconciliationJob") Job reconciliationJob,
      RunStateService runStateService) {
    this.taskExecutor = taskExecutor;
    this.jobLauncher = jobLauncher;
    this.reconciliationJob = reconciliationJob;
    this.runStateService = runStateService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void launchAfterCommit(ReconciliationCreatedEvent event) {
    try {
      taskExecutor.execute(() -> launch(event));
    } catch (RuntimeException exception) {
      runStateService.markFailed(event.runId(), "Batch queue rejected the run: " + exception.getMessage());
    }
  }

  private void launch(ReconciliationCreatedEvent event) {
    try {
      var parameters =
          new JobParametersBuilder()
              .addString("runId", event.runId().toString())
              .addString("inputFile", event.inputFile())
              .addDate("submittedAt", new Date())
              .toJobParameters();
      jobLauncher.run(reconciliationJob, parameters);
    } catch (Exception exception) {
      log.error("Could not launch reconciliation run {}", event.runId(), exception);
      runStateService.markFailed(event.runId(), "Could not launch batch job: " + exception.getMessage());
    }
  }
}

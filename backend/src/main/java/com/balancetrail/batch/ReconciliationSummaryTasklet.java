package com.balancetrail.batch;

import com.balancetrail.service.RunStateService;
import java.util.UUID;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

public class ReconciliationSummaryTasklet implements Tasklet {
  private final UUID runId;
  private final RunStateService runStateService;

  public ReconciliationSummaryTasklet(UUID runId, RunStateService runStateService) {
    this.runId = runId;
    this.runStateService = runStateService;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    runStateService.summarize(runId);
    return RepeatStatus.FINISHED;
  }
}

package com.balancetrail.batch;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.ReconciliationItemEntity;
import com.balancetrail.exception.DuplicateTransactionException;
import com.balancetrail.service.SkippedItemRecorder;
import java.util.UUID;
import org.springframework.batch.core.SkipListener;

public class ReconciliationSkipListener
    implements SkipListener<RawGatewayRecord, ReconciliationItemEntity> {
  private final UUID runId;
  private final SkippedItemRecorder skippedItemRecorder;

  public ReconciliationSkipListener(UUID runId, SkippedItemRecorder skippedItemRecorder) {
    this.runId = runId;
    this.skippedItemRecorder = skippedItemRecorder;
  }

  @Override
  public void onSkipInProcess(RawGatewayRecord item, Throwable throwable) {
    ItemStatus status =
        throwable instanceof DuplicateTransactionException
            ? ItemStatus.DUPLICATE
            : ItemStatus.INVALID;
    skippedItemRecorder.record(runId, item, status, throwable.getMessage());
  }
}

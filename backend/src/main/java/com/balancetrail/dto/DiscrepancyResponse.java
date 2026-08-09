package com.balancetrail.dto;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.ReconciliationItemEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DiscrepancyResponse(
    long lineNumber,
    String transactionId,
    String accountNumber,
    BigDecimal gatewayAmount,
    BigDecimal ledgerAmount,
    LocalDate transactionDate,
    ItemStatus status,
    String reason) {

  public static DiscrepancyResponse from(ReconciliationItemEntity item) {
    return new DiscrepancyResponse(
        item.getLineNumber(),
        item.getGatewayTransactionId(),
        item.getAccountNumber(),
        item.getGatewayAmount(),
        item.getLedgerAmount(),
        item.getTransactionDate(),
        item.getStatus(),
        item.getReason());
  }
}

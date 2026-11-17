package com.balancetrail.dto;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.ReconciliationItemEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One non-matched result. Gateway fields come from the CSV line, ledger fields from the ledger
 * row. A MISSING_IN_GATEWAY row has no CSV line, so its line number and gateway fields are null.
 */
public record DiscrepancyResponse(
    Long lineNumber,
    String transactionId,
    String accountNumber,
    BigDecimal gatewayAmount,
    LocalDate transactionDate,
    String ledgerTransactionId,
    String ledgerAccountNumber,
    BigDecimal ledgerAmount,
    LocalDate ledgerTransactionDate,
    ItemStatus status,
    String reason) {

  public static DiscrepancyResponse from(ReconciliationItemEntity item) {
    return new DiscrepancyResponse(
        item.getLineNumber(),
        item.getGatewayTransactionId(),
        item.getAccountNumber(),
        item.getGatewayAmount(),
        item.getTransactionDate(),
        item.getLedgerTransactionId(),
        item.getLedgerAccountNumber(),
        item.getLedgerAmount(),
        item.getLedgerTransactionDate(),
        item.getStatus(),
        item.getReason());
  }
}

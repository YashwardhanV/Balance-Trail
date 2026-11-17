package com.balancetrail.dto;

import com.balancetrail.domain.SuggestionStatus;
import com.balancetrail.entity.MatchSuggestionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One suggested pair, with both rows spelled out so the analyst can decide without lookups. */
public record MatchSuggestionResponse(
    long id,
    long gatewayLineNumber,
    String gatewayTransactionId,
    String gatewayAccountNumber,
    BigDecimal gatewayAmount,
    LocalDate gatewayDate,
    String ledgerTransactionId,
    String ledgerAccountNumber,
    BigDecimal ledgerAmount,
    LocalDate ledgerDate,
    int idDistance,
    boolean ambiguous,
    SuggestionStatus status,
    String decidedBy,
    Instant decidedAt,
    String decisionNote) {

  public static MatchSuggestionResponse from(MatchSuggestionEntity suggestion) {
    var gateway = suggestion.getGatewayItem();
    var ledger = suggestion.getLedgerItem();
    return new MatchSuggestionResponse(
        suggestion.getId(),
        gateway.getLineNumber(),
        gateway.getGatewayTransactionId(),
        gateway.getAccountNumber(),
        gateway.getGatewayAmount(),
        gateway.getTransactionDate(),
        ledger.getLedgerTransactionId(),
        ledger.getLedgerAccountNumber(),
        ledger.getLedgerAmount(),
        ledger.getLedgerTransactionDate(),
        suggestion.getIdDistance(),
        suggestion.isAmbiguous(),
        suggestion.getStatus(),
        suggestion.getDecidedBy() == null ? null : suggestion.getDecidedBy().getUsername(),
        suggestion.getDecidedAt(),
        suggestion.getDecisionNote());
  }
}

package com.balancetrail.service;

import com.balancetrail.domain.GatewayTransaction;
import com.balancetrail.domain.ItemStatus;
import com.balancetrail.domain.MatchDecision;
import com.balancetrail.entity.LedgerTransactionEntity;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TransactionMatcher {

  public MatchDecision match(
      GatewayTransaction gateway, Optional<LedgerTransactionEntity> ledgerTransaction) {
    if (ledgerTransaction.isEmpty()) {
      return new MatchDecision(
          ItemStatus.MISSING_IN_LEDGER, "No ledger transaction has this transaction ID");
    }

    var ledger = ledgerTransaction.get();
    if (gateway.amount().compareTo(ledger.getAmount()) != 0) {
      return new MatchDecision(
          ItemStatus.AMOUNT_MISMATCH,
          "Gateway amount " + gateway.amount() + " differs from ledger amount " + ledger.getAmount());
    }

    return new MatchDecision(ItemStatus.MATCHED, "Transaction ID and amount match the ledger");
  }
}

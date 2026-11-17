package com.balancetrail.service;

import com.balancetrail.domain.GatewayTransaction;
import com.balancetrail.domain.ItemStatus;
import com.balancetrail.domain.MatchDecision;
import com.balancetrail.entity.LedgerTransactionEntity;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Compares one gateway transaction with its ledger row (same transaction ID).
 *
 * <p>Three fields are compared: amount, account number and date. A gateway usually settles a day
 * or two after the ledger records the payment, so dates up to {@code app.matching.date-tolerance-days}
 * apart still count as equal.
 *
 * <p>If several fields differ, the status is the most serious one (amount, then account, then
 * date) and the reason lists all of them.
 */
@Component
public class TransactionMatcher {
  private final int dateToleranceDays;

  public TransactionMatcher(@Value("${app.matching.date-tolerance-days:2}") int dateToleranceDays) {
    if (dateToleranceDays < 0) {
      throw new IllegalArgumentException("app.matching.date-tolerance-days must be 0 or more");
    }
    this.dateToleranceDays = dateToleranceDays;
  }

  public MatchDecision match(
      GatewayTransaction gateway, Optional<LedgerTransactionEntity> ledgerTransaction) {
    if (ledgerTransaction.isEmpty()) {
      return new MatchDecision(
          ItemStatus.MISSING_IN_LEDGER, "No ledger transaction has this transaction ID");
    }
    var ledger = ledgerTransaction.get();

    List<String> differences = new ArrayList<>();
    ItemStatus status = null;

    if (gateway.amount().compareTo(ledger.getAmount()) != 0) {
      differences.add("amount (gateway " + gateway.amount() + ", ledger " + ledger.getAmount() + ")");
      status = ItemStatus.AMOUNT_MISMATCH;
    }
    if (!gateway.accountNumber().equals(ledger.getAccountNumber())) {
      differences.add(
          "account (gateway " + gateway.accountNumber() + ", ledger " + ledger.getAccountNumber() + ")");
      status = status == null ? ItemStatus.ACCOUNT_MISMATCH : status;
    }
    long daysApart =
        Math.abs(ChronoUnit.DAYS.between(ledger.getTransactionDate(), gateway.transactionDate()));
    if (daysApart > dateToleranceDays) {
      differences.add(
          "date (gateway " + gateway.transactionDate() + ", ledger " + ledger.getTransactionDate()
              + ", " + daysApart + " days apart, allowed " + dateToleranceDays + ")");
      status = status == null ? ItemStatus.DATE_MISMATCH : status;
    }

    if (status != null) {
      return new MatchDecision(status, "Differs in " + String.join("; ", differences));
    }
    String reason =
        daysApart == 0
            ? "Amount, account and date match the ledger"
            : "Amount and account match; dates are " + daysApart + " day(s) apart, within the allowed "
                + dateToleranceDays;
    return new MatchDecision(ItemStatus.MATCHED, reason);
  }
}

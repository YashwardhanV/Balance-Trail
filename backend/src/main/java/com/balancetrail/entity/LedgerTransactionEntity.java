package com.balancetrail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ledger_transaction")
public class LedgerTransactionEntity {

  @Id
  @Column(name = "transaction_ref", length = 64)
  private String transactionRef;

  @Column(name = "account_number", nullable = false, length = 32)
  private String accountNumber;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "transaction_date", nullable = false)
  private LocalDate transactionDate;

  @Column(length = 160)
  private String description;

  protected LedgerTransactionEntity() {}

  public LedgerTransactionEntity(
      String transactionRef,
      String accountNumber,
      BigDecimal amount,
      LocalDate transactionDate,
      String description) {
    this.transactionRef = transactionRef;
    this.accountNumber = accountNumber;
    this.amount = amount;
    this.transactionDate = transactionDate;
    this.description = description;
  }

  public String getTransactionRef() {
    return transactionRef;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }
}

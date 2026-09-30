package com.balancetrail.entity;

import com.balancetrail.domain.GatewayTransaction;
import com.balancetrail.domain.ItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "reconciliation_item")
public class ReconciliationItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reconciliation_item_seq")
  @SequenceGenerator(
      name = "reconciliation_item_seq",
      sequenceName = "reconciliation_item_seq",
      allocationSize = 100)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id", nullable = false)
  private ReconciliationRunEntity run;

  @Column(name = "line_number", nullable = false)
  private long lineNumber;

  @Column(name = "gateway_transaction_id", length = 64)
  private String gatewayTransactionId;

  @Column(name = "account_number", length = 32)
  private String accountNumber;

  @Column(name = "gateway_amount", precision = 19, scale = 2)
  private BigDecimal gatewayAmount;

  @Column(name = "ledger_transaction_id", length = 64)
  private String ledgerTransactionId;

  @Column(name = "ledger_amount", precision = 19, scale = 2)
  private BigDecimal ledgerAmount;

  @Column(name = "transaction_date")
  private LocalDate transactionDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ItemStatus status;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ReconciliationItemEntity() {}

  /** A line that passed validation: MATCHED, AMOUNT_MISMATCH, MISSING_IN_LEDGER or DUPLICATE. */
  public static ReconciliationItemEntity result(
      ReconciliationRunEntity run,
      GatewayTransaction gateway,
      LedgerTransactionEntity ledger,
      ItemStatus status,
      String reason) {
    var item =
        base(
            run,
            gateway.lineNumber(),
            gateway.transactionId(),
            gateway.accountNumber(),
            status,
            reason);
    item.gatewayAmount = gateway.amount();
    item.transactionDate = gateway.transactionDate();
    if (ledger != null) {
      item.ledgerTransactionId = ledger.getTransactionRef();
      item.ledgerAmount = ledger.getAmount();
    }
    return item;
  }

  /** A line that failed validation. Over-long values are cut to fit their columns. */
  public static ReconciliationItemEntity invalid(
      ReconciliationRunEntity run,
      long lineNumber,
      String transactionId,
      String accountNumber,
      String reason) {
    return base(
        run,
        lineNumber,
        cut(transactionId, 64),
        cut(accountNumber, 32),
        ItemStatus.INVALID,
        cut(reason, 500));
  }

  private static ReconciliationItemEntity base(
      ReconciliationRunEntity run,
      long lineNumber,
      String gatewayTransactionId,
      String accountNumber,
      ItemStatus status,
      String reason) {
    var item = new ReconciliationItemEntity();
    item.run = run;
    item.lineNumber = lineNumber;
    item.gatewayTransactionId = gatewayTransactionId;
    item.accountNumber = accountNumber;
    item.status = status;
    item.reason = reason;
    item.createdAt = Instant.now();
    return item;
  }

  private static String cut(String value, int maxLength) {
    return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  public Long getId() {
    return id;
  }

  public long getLineNumber() {
    return lineNumber;
  }

  public String getGatewayTransactionId() {
    return gatewayTransactionId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public BigDecimal getGatewayAmount() {
    return gatewayAmount;
  }

  public String getLedgerTransactionId() {
    return ledgerTransactionId;
  }

  public BigDecimal getLedgerAmount() {
    return ledgerAmount;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  public ItemStatus getStatus() {
    return status;
  }

  public String getReason() {
    return reason;
  }
}

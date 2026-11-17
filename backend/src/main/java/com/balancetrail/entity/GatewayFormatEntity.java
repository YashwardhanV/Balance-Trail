package com.balancetrail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A saved column mapping for one gateway's export. Never edited after it is saved: a run records
 * which format read its file, so changing a format would silently change what old runs mean.
 */
@Entity
@Table(name = "gateway_format")
public class GatewayFormatEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 80)
  private String name;

  /** The file's header row as a JSON array, exactly as uploaded. */
  @Column(name = "headers_json", nullable = false, length = 4000)
  private String headersJson;

  @Column(name = "transaction_id_column", nullable = false, length = 100)
  private String transactionIdColumn;

  @Column(name = "account_number_column", nullable = false, length = 100)
  private String accountNumberColumn;

  @Column(name = "amount_column", nullable = false, length = 100)
  private String amountColumn;

  @Column(name = "transaction_date_column", nullable = false, length = 100)
  private String transactionDateColumn;

  @Column(name = "date_format", nullable = false, length = 30)
  private String dateFormat;

  /** What produced the first suggestion: GEMINI, HEADER_RULES or MANUAL. */
  @Column(name = "suggested_by", nullable = false, length = 20)
  private String suggestedBy;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by", nullable = false)
  private AppUserEntity createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected GatewayFormatEntity() {}

  public GatewayFormatEntity(
      String name,
      String headersJson,
      String transactionIdColumn,
      String accountNumberColumn,
      String amountColumn,
      String transactionDateColumn,
      String dateFormat,
      String suggestedBy,
      AppUserEntity createdBy) {
    this.name = name;
    this.headersJson = headersJson;
    this.transactionIdColumn = transactionIdColumn;
    this.accountNumberColumn = accountNumberColumn;
    this.amountColumn = amountColumn;
    this.transactionDateColumn = transactionDateColumn;
    this.dateFormat = dateFormat;
    this.suggestedBy = suggestedBy;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getHeadersJson() {
    return headersJson;
  }

  public String getTransactionIdColumn() {
    return transactionIdColumn;
  }

  public String getAccountNumberColumn() {
    return accountNumberColumn;
  }

  public String getAmountColumn() {
    return amountColumn;
  }

  public String getTransactionDateColumn() {
    return transactionDateColumn;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public String getSuggestedBy() {
    return suggestedBy;
  }

  public AppUserEntity getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

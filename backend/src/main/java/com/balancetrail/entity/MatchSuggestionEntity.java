package com.balancetrail.entity;

import com.balancetrail.domain.SuggestionStatus;
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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * "This gateway line and this ledger row are probably the same payment." Created by the batch
 * job, decided by an analyst. Deciding never changes the two original results.
 */
@Entity
@Table(name = "match_suggestion")
public class MatchSuggestionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "run_id", nullable = false)
  private UUID runId;

  /** The MISSING_IN_LEDGER row (from the gateway file). */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "gateway_item_id", nullable = false)
  private ReconciliationItemEntity gatewayItem;

  /** The MISSING_IN_GATEWAY row (from the ledger). */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ledger_item_id", nullable = false)
  private ReconciliationItemEntity ledgerItem;

  @Column(name = "id_distance", nullable = false)
  private int idDistance;

  @Column(nullable = false)
  private boolean ambiguous;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SuggestionStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "decided_by")
  private AppUserEntity decidedBy;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decision_note", length = 200)
  private String decisionNote;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MatchSuggestionEntity() {}

  public void confirm(AppUserEntity analyst) {
    decide(SuggestionStatus.CONFIRMED, analyst, null);
  }

  public void reject(AppUserEntity analyst, String note) {
    decide(SuggestionStatus.REJECTED, analyst, note);
  }

  /** Closed because one of its rows was confirmed in a different pair. */
  public void closeBecauseOtherPairConfirmed(AppUserEntity analyst) {
    decide(SuggestionStatus.REJECTED, analyst, "Closed: another pair was confirmed for one of these rows");
  }

  private void decide(SuggestionStatus newStatus, AppUserEntity analyst, String note) {
    status = newStatus;
    decidedBy = analyst;
    decidedAt = Instant.now();
    decisionNote = note == null || note.isBlank() ? null : note.substring(0, Math.min(200, note.length()));
  }

  public Long getId() {
    return id;
  }

  public UUID getRunId() {
    return runId;
  }

  public ReconciliationItemEntity getGatewayItem() {
    return gatewayItem;
  }

  public ReconciliationItemEntity getLedgerItem() {
    return ledgerItem;
  }

  public int getIdDistance() {
    return idDistance;
  }

  public boolean isAmbiguous() {
    return ambiguous;
  }

  public SuggestionStatus getStatus() {
    return status;
  }

  public AppUserEntity getDecidedBy() {
    return decidedBy;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public String getDecisionNote() {
    return decisionNote;
  }
}

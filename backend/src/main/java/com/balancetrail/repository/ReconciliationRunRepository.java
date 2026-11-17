package com.balancetrail.repository;

import com.balancetrail.domain.RunStatus;
import com.balancetrail.entity.ReconciliationRunEntity;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReconciliationRunRepository
    extends JpaRepository<ReconciliationRunEntity, UUID> {

  Optional<ReconciliationRunEntity> findByIdAndOwnerUsernameIgnoreCase(UUID id, String username);

  /**
   * The run to reuse for an identical upload: same user, same file, same ledger, same format
   * (0 = standard layout), not failed. Mirrors the partial unique index uk_run_owner_hash_ledger_format.
   */
  @Query(
      "select r from ReconciliationRunEntity r where r.owner.id = :ownerId and r.fileSha256 = :sha"
          + " and r.ledgerVersion = :ledgerVersion and coalesce(r.gatewayFormatId, 0) = :formatId"
          + " and r.status <> com.balancetrail.domain.RunStatus.FAILED")
  Optional<ReconciliationRunEntity> findReusableRun(
      @Param("ownerId") Long ownerId,
      @Param("sha") String sha,
      @Param("ledgerVersion") long ledgerVersion,
      @Param("formatId") long formatId);

  Page<ReconciliationRunEntity> findAllByOwnerUsernameIgnoreCase(
      String username, Pageable pageable);

  boolean existsByStatusIn(Collection<RunStatus> statuses);
}

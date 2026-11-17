package com.balancetrail.repository;

import com.balancetrail.domain.RunStatus;
import com.balancetrail.entity.LedgerImportEntity;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerImportRepository extends JpaRepository<LedgerImportEntity, Long> {

  /** The current ledger version: the id of the latest import, or 0 for the seeded demo ledger. */
  @Query("select coalesce(max(l.id), 0) from LedgerImportEntity l")
  long currentVersion();

  boolean existsByStatusIn(Collection<RunStatus> statuses);

  // Load the uploader in the same query, so building the response never needs a second query
  // (or an open session).
  @EntityGraph(attributePaths = "uploadedBy")
  Page<LedgerImportEntity> findAllBy(Pageable pageable);

  @EntityGraph(attributePaths = "uploadedBy")
  Optional<LedgerImportEntity> findWithUploaderById(long id);
}

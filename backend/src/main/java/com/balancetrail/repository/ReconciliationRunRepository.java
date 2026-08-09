package com.balancetrail.repository;

import com.balancetrail.entity.ReconciliationRunEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository
    extends JpaRepository<ReconciliationRunEntity, UUID> {

  Optional<ReconciliationRunEntity> findByIdAndOwnerUsernameIgnoreCase(UUID id, String username);

  Optional<ReconciliationRunEntity> findByOwnerIdAndFileSha256(Long ownerId, String fileSha256);

  Optional<ReconciliationRunEntity> findByOwnerUsernameIgnoreCaseAndFileSha256(
      String username, String fileSha256);

  Page<ReconciliationRunEntity> findAllByOwnerUsernameIgnoreCase(
      String username, Pageable pageable);
}

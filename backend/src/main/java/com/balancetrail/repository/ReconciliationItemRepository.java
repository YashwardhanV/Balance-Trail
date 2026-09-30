package com.balancetrail.repository;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.ReconciliationItemEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReconciliationItemRepository
    extends JpaRepository<ReconciliationItemEntity, Long> {

  Page<ReconciliationItemEntity> findAllByRunIdAndStatusIn(
      UUID runId, Collection<ItemStatus> statuses, Pageable pageable);

  @Query(
      "select i.status, count(i) from ReconciliationItemEntity i "
          + "where i.run.id = :runId group by i.status")
  List<Object[]> countByStatus(@Param("runId") UUID runId);
}

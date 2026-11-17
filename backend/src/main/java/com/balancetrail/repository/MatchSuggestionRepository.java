package com.balancetrail.repository;

import com.balancetrail.domain.SuggestionStatus;
import com.balancetrail.entity.MatchSuggestionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchSuggestionRepository extends JpaRepository<MatchSuggestionEntity, Long> {

  // Both rows and the decider are loaded in the same query, so a page is one SELECT.
  @EntityGraph(attributePaths = {"gatewayItem", "ledgerItem", "decidedBy"})
  Page<MatchSuggestionEntity> findAllByRunId(UUID runId, Pageable pageable);

  @EntityGraph(attributePaths = {"gatewayItem", "ledgerItem", "decidedBy"})
  Optional<MatchSuggestionEntity> findByIdAndRunId(Long id, UUID runId);

  /** Other open suggestions that share a row with the one just confirmed. */
  @Query(
      "select s from MatchSuggestionEntity s where s.status = :status and s.id <> :id"
          + " and (s.gatewayItem.id = :gatewayItemId or s.ledgerItem.id = :ledgerItemId)")
  List<MatchSuggestionEntity> findOverlapping(
      @Param("id") Long id,
      @Param("gatewayItemId") Long gatewayItemId,
      @Param("ledgerItemId") Long ledgerItemId,
      @Param("status") SuggestionStatus status);
}

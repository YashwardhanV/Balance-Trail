package com.balancetrail.service;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.exception.ResourceNotFoundException;
import com.balancetrail.repository.ReconciliationItemRepository;
import com.balancetrail.repository.ReconciliationRunRepository;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunStateService {
  private final ReconciliationRunRepository runRepository;
  private final ReconciliationItemRepository itemRepository;
  private final JdbcTemplate jdbcTemplate;

  public RunStateService(
      ReconciliationRunRepository runRepository,
      ReconciliationItemRepository itemRepository,
      JdbcTemplate jdbcTemplate) {
    this.runRepository = runRepository;
    this.itemRepository = itemRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public void markRunning(UUID runId) {
    var run = find(runId);
    run.markRunning();
  }

  @Transactional
  public void summarize(UUID runId) {
    Map<ItemStatus, Long> counts = new EnumMap<>(ItemStatus.class);
    for (Object[] row : itemRepository.countByStatus(runId)) {
      counts.put((ItemStatus) row[0], (Long) row[1]);
    }
    Long suggestions =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM match_suggestion WHERE run_id = ?", Long.class, runId);
    find(runId).complete(counts, suggestions == null ? 0 : suggestions);
  }

  @Transactional
  public void markFailed(UUID runId, String message) {
    find(runId).fail(message);
  }

  private com.balancetrail.entity.ReconciliationRunEntity find(UUID runId) {
    return runRepository
        .findById(runId)
        .orElseThrow(() -> new ResourceNotFoundException("Reconciliation run not found"));
  }
}

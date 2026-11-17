package com.balancetrail.service;

import com.balancetrail.domain.SuggestionStatus;
import com.balancetrail.dto.MatchSuggestionResponse;
import com.balancetrail.dto.PageResponse;
import com.balancetrail.entity.MatchSuggestionEntity;
import com.balancetrail.exception.ConflictException;
import com.balancetrail.exception.ResourceNotFoundException;
import com.balancetrail.repository.AppUserRepository;
import com.balancetrail.repository.MatchSuggestionRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists suggested pairs of a run and records the analyst's confirm / reject decision. */
@Service
public class MatchSuggestionService {
  private final ReconciliationService reconciliationService;
  private final MatchSuggestionRepository suggestionRepository;
  private final AppUserRepository userRepository;

  public MatchSuggestionService(
      ReconciliationService reconciliationService,
      MatchSuggestionRepository suggestionRepository,
      AppUserRepository userRepository) {
    this.reconciliationService = reconciliationService;
    this.suggestionRepository = suggestionRepository;
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<MatchSuggestionResponse> list(UUID runId, String username, int page, int size) {
    reconciliationService.ownedRun(runId, username);
    var pageable = PageRequest.of(page, size, Sort.by("id"));
    return PageResponse.from(
        suggestionRepository.findAllByRunId(runId, pageable), MatchSuggestionResponse::from);
  }

  /**
   * Confirms a pair. Any other open suggestion that uses one of the same two rows is closed,
   * because a row can only be one payment. (The database also enforces "at most one confirmed
   * pair per row" with partial unique indexes, in case two requests confirm at the same time.)
   */
  @Transactional
  public MatchSuggestionResponse confirm(UUID runId, long suggestionId, String username) {
    var suggestion = openSuggestion(runId, suggestionId, username);
    var analyst = analyst(username);
    suggestion.confirm(analyst);
    for (var other :
        suggestionRepository.findOverlapping(
            suggestion.getId(),
            suggestion.getGatewayItem().getId(),
            suggestion.getLedgerItem().getId(),
            SuggestionStatus.PENDING)) {
      other.closeBecauseOtherPairConfirmed(analyst);
    }
    return MatchSuggestionResponse.from(suggestion);
  }

  @Transactional
  public MatchSuggestionResponse reject(UUID runId, long suggestionId, String username, String note) {
    var suggestion = openSuggestion(runId, suggestionId, username);
    suggestion.reject(analyst(username), note);
    return MatchSuggestionResponse.from(suggestion);
  }

  private MatchSuggestionEntity openSuggestion(UUID runId, long suggestionId, String username) {
    reconciliationService.ownedRun(runId, username);
    var suggestion =
        suggestionRepository
            .findByIdAndRunId(suggestionId, runId)
            .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found"));
    if (suggestion.getStatus() != SuggestionStatus.PENDING) {
      throw new ConflictException("This suggestion was already " + suggestion.getStatus().name().toLowerCase());
    }
    return suggestion;
  }

  private com.balancetrail.entity.AppUserEntity analyst(String username) {
    return userRepository
        .findByUsernameIgnoreCase(username)
        .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
  }
}

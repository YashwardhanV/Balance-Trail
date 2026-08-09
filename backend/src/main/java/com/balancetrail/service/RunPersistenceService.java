package com.balancetrail.service;

import com.balancetrail.entity.ReconciliationRunEntity;
import com.balancetrail.exception.ResourceNotFoundException;
import com.balancetrail.repository.AppUserRepository;
import com.balancetrail.repository.ReconciliationRunRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunPersistenceService {
  private final AppUserRepository userRepository;
  private final ReconciliationRunRepository runRepository;
  private final ApplicationEventPublisher eventPublisher;

  public RunPersistenceService(
      AppUserRepository userRepository,
      ReconciliationRunRepository runRepository,
      ApplicationEventPublisher eventPublisher) {
    this.userRepository = userRepository;
    this.runRepository = runRepository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public RunCreationDecision createOrReuse(StoredFile storedFile, String username) {
    var owner =
        userRepository
            .findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    var existing = runRepository.findByOwnerIdAndFileSha256(owner.getId(), storedFile.sha256());
    if (existing.isPresent()) {
      return new RunCreationDecision(existing.get(), true);
    }

    var run =
        ReconciliationRunEntity.pending(
            owner,
            storedFile.originalFileName(),
            storedFile.sha256(),
            storedFile.path().toString());
    runRepository.saveAndFlush(run);
    eventPublisher.publishEvent(
        new ReconciliationCreatedEvent(run.getId(), storedFile.path().toString()));
    return new RunCreationDecision(run, false);
  }
}

package com.balancetrail.service;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.dto.DiscrepancyResponse;
import com.balancetrail.dto.PageResponse;
import com.balancetrail.dto.ReconciliationRunResponse;
import com.balancetrail.dto.ReconciliationStartResponse;
import com.balancetrail.dto.ReconciliationSummaryResponse;
import com.balancetrail.entity.ReconciliationRunEntity;
import com.balancetrail.exception.ResourceNotFoundException;
import com.balancetrail.repository.ReconciliationItemRepository;
import com.balancetrail.repository.ReconciliationRunRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReconciliationService {
  private static final List<ItemStatus> DISCREPANCIES =
      List.of(
          ItemStatus.AMOUNT_MISMATCH,
          ItemStatus.MISSING_IN_LEDGER,
          ItemStatus.INVALID,
          ItemStatus.DUPLICATE);

  private final FileStorageService fileStorageService;
  private final RunPersistenceService runPersistenceService;
  private final ReconciliationRunRepository runRepository;
  private final ReconciliationItemRepository itemRepository;

  public ReconciliationService(
      FileStorageService fileStorageService,
      RunPersistenceService runPersistenceService,
      ReconciliationRunRepository runRepository,
      ReconciliationItemRepository itemRepository) {
    this.fileStorageService = fileStorageService;
    this.runPersistenceService = runPersistenceService;
    this.runRepository = runRepository;
    this.itemRepository = itemRepository;
  }

  public ReconciliationStartResponse start(MultipartFile file, String username) {
    StoredFile storedFile = fileStorageService.store(file);
    try {
      RunCreationDecision decision = runPersistenceService.createOrReuse(storedFile, username);
      if (decision.reused()) {
        fileStorageService.deleteQuietly(storedFile.path());
      }
      return new ReconciliationStartResponse(
          ReconciliationRunResponse.from(decision.run()), decision.reused());
    } catch (DataIntegrityViolationException race) {
      fileStorageService.deleteQuietly(storedFile.path());
      ReconciliationRunEntity existing =
          runRepository
              .findByOwnerUsernameIgnoreCaseAndFileSha256(username, storedFile.sha256())
              .orElseThrow(() -> race);
      return new ReconciliationStartResponse(ReconciliationRunResponse.from(existing), true);
    } catch (RuntimeException exception) {
      fileStorageService.deleteQuietly(storedFile.path());
      throw exception;
    }
  }

  @Transactional(readOnly = true)
  public PageResponse<ReconciliationRunResponse> list(String username, int page, int size) {
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    return PageResponse.from(
        runRepository.findAllByOwnerUsernameIgnoreCase(username, pageable),
        ReconciliationRunResponse::from);
  }

  @Transactional(readOnly = true)
  public ReconciliationRunResponse get(UUID id, String username) {
    return ReconciliationRunResponse.from(ownedRun(id, username));
  }

  @Transactional(readOnly = true)
  public ReconciliationSummaryResponse summary(UUID id, String username) {
    return ReconciliationSummaryResponse.from(ownedRun(id, username));
  }

  @Transactional(readOnly = true)
  public PageResponse<DiscrepancyResponse> discrepancies(
      UUID id, String username, int page, int size) {
    ownedRun(id, username);
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "lineNumber"));
    return PageResponse.from(
        itemRepository.findAllByRunIdAndStatusIn(id, DISCREPANCIES, pageable),
        DiscrepancyResponse::from);
  }

  private ReconciliationRunEntity ownedRun(UUID id, String username) {
    return runRepository
        .findByIdAndOwnerUsernameIgnoreCase(id, username)
        .orElseThrow(() -> new ResourceNotFoundException("Reconciliation run not found"));
  }
}

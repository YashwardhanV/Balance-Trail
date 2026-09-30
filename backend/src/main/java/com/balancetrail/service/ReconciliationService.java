package com.balancetrail.service;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.dto.DiscrepancyResponse;
import com.balancetrail.dto.PageResponse;
import com.balancetrail.dto.ReconciliationRunResponse;
import com.balancetrail.dto.ReconciliationStartResponse;
import com.balancetrail.dto.ReconciliationSummaryResponse;
import com.balancetrail.entity.ReconciliationRunEntity;
import com.balancetrail.exception.ResourceNotFoundException;
import com.balancetrail.repository.AppUserRepository;
import com.balancetrail.repository.ReconciliationItemRepository;
import com.balancetrail.repository.ReconciliationRunRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReconciliationService {
  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
  private static final List<ItemStatus> DISCREPANCIES =
      List.of(
          ItemStatus.AMOUNT_MISMATCH,
          ItemStatus.MISSING_IN_LEDGER,
          ItemStatus.INVALID,
          ItemStatus.DUPLICATE);

  private final FileStorageService fileStorageService;
  private final AppUserRepository userRepository;
  private final ReconciliationRunRepository runRepository;
  private final ReconciliationItemRepository itemRepository;
  private final RunStateService runStateService;
  private final JobLauncher jobLauncher;
  private final Job reconciliationJob;

  public ReconciliationService(
      FileStorageService fileStorageService,
      AppUserRepository userRepository,
      ReconciliationRunRepository runRepository,
      ReconciliationItemRepository itemRepository,
      RunStateService runStateService,
      @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
      @Qualifier("reconciliationJob") Job reconciliationJob) {
    this.fileStorageService = fileStorageService;
    this.userRepository = userRepository;
    this.runRepository = runRepository;
    this.itemRepository = itemRepository;
    this.runStateService = runStateService;
    this.jobLauncher = jobLauncher;
    this.reconciliationJob = reconciliationJob;
  }

  /**
   * Stores the CSV, creates a PENDING run and starts the batch job in the background.
   *
   * <p>This method is deliberately not {@code @Transactional}: {@code runRepository.save()} commits
   * immediately, so the run row already exists when the batch thread looks for it.
   */
  public ReconciliationStartResponse start(MultipartFile file, String username) {
    StoredFile storedFile = fileStorageService.store(file);
    ReconciliationRunEntity run;
    try {
      var owner =
          userRepository
              .findByUsernameIgnoreCase(username)
              .orElseThrow(
                  () -> new ResourceNotFoundException("Authenticated user no longer exists"));

      // Same bytes uploaded before by this user? Return that run instead of starting a new one.
      var existing = runRepository.findByOwnerIdAndFileSha256(owner.getId(), storedFile.sha256());
      if (existing.isPresent()) {
        fileStorageService.deleteQuietly(storedFile.path());
        return new ReconciliationStartResponse(
            ReconciliationRunResponse.from(existing.get()), true);
      }

      run =
          runRepository.save(
              ReconciliationRunEntity.pending(
                  owner,
                  storedFile.originalFileName(),
                  storedFile.sha256(),
                  storedFile.path().toString()));
    } catch (RuntimeException exception) {
      // Includes the rare case where two identical uploads race: the unique
      // (owner_id, file_sha256) constraint rejects the second one with 409 Conflict.
      fileStorageService.deleteQuietly(storedFile.path());
      throw exception;
    }

    launchJob(run.getId(), storedFile.path().toString());
    return new ReconciliationStartResponse(ReconciliationRunResponse.from(run), false);
  }

  private void launchJob(UUID runId, String inputFile) {
    try {
      var parameters =
          new JobParametersBuilder()
              .addString("runId", runId.toString())
              .addString("inputFile", inputFile)
              .toJobParameters();
      jobLauncher.run(reconciliationJob, parameters); // returns at once; a worker thread runs it
    } catch (Exception exception) {
      log.error("Could not launch reconciliation run {}", runId, exception);
      runStateService.markFailed(runId, "Could not launch batch job: " + exception.getMessage());
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

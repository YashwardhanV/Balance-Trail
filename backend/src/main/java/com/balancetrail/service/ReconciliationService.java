package com.balancetrail.service;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.domain.RunStatus;
import com.balancetrail.dto.DiscrepancyResponse;
import com.balancetrail.dto.PageResponse;
import com.balancetrail.dto.ReconciliationRunResponse;
import com.balancetrail.dto.ReconciliationStartResponse;
import com.balancetrail.dto.ReconciliationSummaryResponse;
import com.balancetrail.entity.ReconciliationRunEntity;
import com.balancetrail.exception.ConflictException;
import com.balancetrail.exception.ResourceNotFoundException;
import com.balancetrail.repository.AppUserRepository;
import com.balancetrail.repository.LedgerImportRepository;
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
  /** Every status except MATCHED. */
  static final List<ItemStatus> DISCREPANCIES =
      List.of(
          ItemStatus.AMOUNT_MISMATCH,
          ItemStatus.ACCOUNT_MISMATCH,
          ItemStatus.DATE_MISMATCH,
          ItemStatus.MISSING_IN_LEDGER,
          ItemStatus.MISSING_IN_GATEWAY,
          ItemStatus.INVALID,
          ItemStatus.DUPLICATE);

  static final List<RunStatus> ACTIVE = List.of(RunStatus.PENDING, RunStatus.RUNNING);

  private final FileStorageService fileStorageService;
  private final AppUserRepository userRepository;
  private final ReconciliationRunRepository runRepository;
  private final ReconciliationItemRepository itemRepository;
  private final LedgerImportRepository ledgerImportRepository;
  private final RunStateService runStateService;
  private final GatewayFormatService formatService;
  private final JobLauncher jobLauncher;
  private final Job reconciliationJob;

  public ReconciliationService(
      FileStorageService fileStorageService,
      AppUserRepository userRepository,
      ReconciliationRunRepository runRepository,
      ReconciliationItemRepository itemRepository,
      LedgerImportRepository ledgerImportRepository,
      RunStateService runStateService,
      GatewayFormatService formatService,
      @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
      @Qualifier("reconciliationJob") Job reconciliationJob) {
    this.fileStorageService = fileStorageService;
    this.userRepository = userRepository;
    this.runRepository = runRepository;
    this.itemRepository = itemRepository;
    this.ledgerImportRepository = ledgerImportRepository;
    this.runStateService = runStateService;
    this.formatService = formatService;
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
    return start(file, username, null);
  }

  /**
   * @param formatId a saved gateway format to read the file with, or null for the standard
   *     4-column layout
   */
  public ReconciliationStartResponse start(MultipartFile file, String username, Long formatId) {
    var format = formatId == null ? null : formatService.find(formatId);
    StoredFile storedFile =
        format == null
            ? fileStorageService.store(file, FileStorageService.GATEWAY_HEADER)
            : fileStorageService.store(file, formatService.headerCheck(format));
    ReconciliationRunEntity run;
    try {
      // Comparing against a ledger that is half-way through an import would give mixed results.
      if (ledgerImportRepository.existsByStatusIn(ACTIVE)) {
        throw new ConflictException(
            "A ledger import is in progress. Start the reconciliation when it has finished.");
      }
      var owner =
          userRepository
              .findByUsernameIgnoreCase(username)
              .orElseThrow(
                  () -> new ResourceNotFoundException("Authenticated user no longer exists"));
      long ledgerVersion = ledgerImportRepository.currentVersion();

      // Same bytes, same ledger, same format, and the earlier run did not fail? Return that run
      // instead of starting a new one. A FAILED run, or a run against an older ledger, is not reused.
      var existing =
          runRepository.findReusableRun(
              owner.getId(), storedFile.sha256(), ledgerVersion, formatId == null ? 0L : formatId);
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
                  storedFile.path().toString(),
                  ledgerVersion,
                  formatId));
    } catch (RuntimeException exception) {
      // Includes the rare case where two identical uploads race: the unique index on
      // (owner_id, file_sha256, ledger_version, format) rejects the second one with 409 Conflict.
      fileStorageService.deleteQuietly(storedFile.path());
      throw exception;
    }

    launchJob(run.getId(), storedFile.path().toString(), formatId);
    return new ReconciliationStartResponse(ReconciliationRunResponse.from(run), false);
  }

  private void launchJob(UUID runId, String inputFile, Long formatId) {
    try {
      var parameters =
          new JobParametersBuilder()
              .addString("runId", runId.toString())
              .addString("inputFile", inputFile)
              .addString("formatId", formatId == null ? "0" : formatId.toString())
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
    // File order first; ledger-only rows (no line number) come last, ordered by ledger ID.
    // PostgreSQL puts NULLs last in ascending order, which is what we want here.
    // (Spring Data's nullsLast() is not supported by Hibernate criteria queries.)
    var pageable =
        PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order.asc("lineNumber"), Sort.Order.asc("ledgerTransactionId")));
    return PageResponse.from(
        itemRepository.findAllByRunIdAndStatusIn(id, DISCREPANCIES, pageable),
        DiscrepancyResponse::from);
  }

  ReconciliationRunEntity ownedRun(UUID id, String username) {
    return runRepository
        .findByIdAndOwnerUsernameIgnoreCase(id, username)
        .orElseThrow(() -> new ResourceNotFoundException("Reconciliation run not found"));
  }
}

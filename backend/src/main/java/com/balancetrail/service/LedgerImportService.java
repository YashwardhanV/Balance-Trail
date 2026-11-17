package com.balancetrail.service;

import com.balancetrail.domain.RunStatus;
import com.balancetrail.dto.LedgerImportErrorResponse;
import com.balancetrail.dto.LedgerImportResponse;
import com.balancetrail.dto.PageResponse;
import com.balancetrail.entity.LedgerImportEntity;
import com.balancetrail.exception.ConflictException;
import com.balancetrail.exception.ResourceNotFoundException;
import com.balancetrail.repository.AppUserRepository;
import com.balancetrail.repository.LedgerImportRepository;
import com.balancetrail.repository.ReconciliationRunRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Uploads of the company ledger. The ledger is shared by all analysts (it is the company's
 * books), so every analyst sees every import.
 */
@Service
public class LedgerImportService {
  private static final Logger log = LoggerFactory.getLogger(LedgerImportService.class);
  private static final List<RunStatus> ACTIVE = List.of(RunStatus.PENDING, RunStatus.RUNNING);

  private final FileStorageService fileStorageService;
  private final AppUserRepository userRepository;
  private final LedgerImportRepository importRepository;
  private final ReconciliationRunRepository runRepository;
  private final LedgerImportStateService stateService;
  private final JdbcTemplate jdbcTemplate;
  private final JobLauncher jobLauncher;
  private final Job ledgerImportJob;

  public LedgerImportService(
      FileStorageService fileStorageService,
      AppUserRepository userRepository,
      LedgerImportRepository importRepository,
      ReconciliationRunRepository runRepository,
      LedgerImportStateService stateService,
      JdbcTemplate jdbcTemplate,
      @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
      @Qualifier("ledgerImportJob") Job ledgerImportJob) {
    this.fileStorageService = fileStorageService;
    this.userRepository = userRepository;
    this.importRepository = importRepository;
    this.runRepository = runRepository;
    this.stateService = stateService;
    this.jdbcTemplate = jdbcTemplate;
    this.jobLauncher = jobLauncher;
    this.ledgerImportJob = ledgerImportJob;
  }

  /**
   * Stores the ledger CSV and starts the import job in the background. Like
   * {@link ReconciliationService#start}, deliberately not {@code @Transactional}.
   *
   * <p>Refused with 409 while anything else is running: changing the ledger in the middle of a
   * reconciliation would compare half the file against the old ledger and half against the new.
   * (The check and the insert are two steps, so two requests arriving in the same instant could
   * both pass. Acceptable for a small team; a database lock would close that gap.)
   */
  public LedgerImportResponse start(MultipartFile file, String username) {
    StoredFile storedFile = fileStorageService.store(file, FileStorageService.LEDGER_HEADER);
    LedgerImportEntity ledgerImport;
    try {
      if (importRepository.existsByStatusIn(ACTIVE) || runRepository.existsByStatusIn(ACTIVE)) {
        throw new ConflictException(
            "A reconciliation or ledger import is still running. Try again when it has finished.");
      }
      var uploader =
          userRepository
              .findByUsernameIgnoreCase(username)
              .orElseThrow(
                  () -> new ResourceNotFoundException("Authenticated user no longer exists"));
      ledgerImport =
          importRepository.save(
              LedgerImportEntity.pending(
                  uploader,
                  storedFile.originalFileName(),
                  storedFile.sha256(),
                  storedFile.path().toString()));
    } catch (RuntimeException exception) {
      fileStorageService.deleteQuietly(storedFile.path());
      throw exception;
    }

    try {
      var parameters =
          new JobParametersBuilder()
              .addString("importId", ledgerImport.getId().toString())
              .addString("inputFile", storedFile.path().toString())
              .toJobParameters();
      jobLauncher.run(ledgerImportJob, parameters);
    } catch (Exception exception) {
      log.error("Could not launch ledger import {}", ledgerImport.getId(), exception);
      stateService.markFailed(ledgerImport.getId(), "Could not launch batch job: " + exception.getMessage());
    }
    return get(ledgerImport.getId());
  }

  @Transactional(readOnly = true)
  public PageResponse<LedgerImportResponse> list(int page, int size) {
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    return PageResponse.from(importRepository.findAllBy(pageable), LedgerImportResponse::from);
  }

  public LedgerImportResponse get(long id) {
    return LedgerImportResponse.from(
        importRepository
            .findWithUploaderById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ledger import not found")));
  }

  /** The rejected lines of one import, in file order. */
  @Transactional(readOnly = true)
  public PageResponse<LedgerImportErrorResponse> errors(long id, int page, int size) {
    find(id);
    Long total =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ledger_import_error WHERE import_id = ?", Long.class, id);
    List<LedgerImportErrorResponse> rows =
        jdbcTemplate.query(
            "SELECT line_number, transaction_id, reason FROM ledger_import_error"
                + " WHERE import_id = ? ORDER BY line_number LIMIT ? OFFSET ?",
            (resultSet, rowNumber) ->
                new LedgerImportErrorResponse(
                    resultSet.getLong(1), resultSet.getString(2), resultSet.getString(3)),
            id,
            size,
            (long) page * size);
    var pageable = PageRequest.of(page, size);
    return PageResponse.from(new PageImpl<>(rows, pageable, total == null ? 0 : total), row -> row);
  }

  private LedgerImportEntity find(long id) {
    return importRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Ledger import not found"));
  }
}

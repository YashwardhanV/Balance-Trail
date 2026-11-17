package com.balancetrail.controller;

import com.balancetrail.dto.LedgerImportErrorResponse;
import com.balancetrail.dto.LedgerImportResponse;
import com.balancetrail.dto.PageResponse;
import com.balancetrail.service.LedgerImportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/ledger-imports")
public class LedgerImportController {
  private final LedgerImportService ledgerImportService;

  public LedgerImportController(LedgerImportService ledgerImportService) {
    this.ledgerImportService = ledgerImportService;
  }

  @Operation(summary = "Upload a ledger CSV; new IDs are added, existing IDs are updated")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<LedgerImportResponse> create(
      @RequestPart("file") MultipartFile file, Principal principal) {
    var response = ledgerImportService.start(file, principal.getName());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .location(URI.create("/ledger-imports/" + response.id()))
        .body(response);
  }

  @GetMapping
  public PageResponse<LedgerImportResponse> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ledgerImportService.list(page, size);
  }

  @GetMapping("/{id}")
  public LedgerImportResponse get(@PathVariable long id) {
    return ledgerImportService.get(id);
  }

  @Operation(summary = "Rejected lines of a ledger import, in file order")
  @GetMapping("/{id}/errors")
  public PageResponse<LedgerImportErrorResponse> errors(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ledgerImportService.errors(id, page, size);
  }
}

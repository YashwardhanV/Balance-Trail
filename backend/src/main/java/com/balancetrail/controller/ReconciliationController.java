package com.balancetrail.controller;

import com.balancetrail.dto.DiscrepancyResponse;
import com.balancetrail.dto.PageResponse;
import com.balancetrail.dto.ReconciliationRunResponse;
import com.balancetrail.dto.ReconciliationStartResponse;
import com.balancetrail.dto.ReconciliationSummaryResponse;
import com.balancetrail.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;
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
@RequestMapping("/reconciliations")
public class ReconciliationController {
  private final ReconciliationService reconciliationService;

  public ReconciliationController(ReconciliationService reconciliationService) {
    this.reconciliationService = reconciliationService;
  }

  @Operation(summary = "Upload a CSV and start a reconciliation")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ReconciliationStartResponse> create(
      @RequestPart("file") MultipartFile file, Principal principal) {
    var response = reconciliationService.start(file, principal.getName());
    HttpStatus status = response.idempotentReplay() ? HttpStatus.OK : HttpStatus.ACCEPTED;
    return ResponseEntity.status(status)
        .location(URI.create("/reconciliations/" + response.reconciliation().id()))
        .body(response);
  }

  @GetMapping
  public PageResponse<ReconciliationRunResponse> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Principal principal) {
    return reconciliationService.list(principal.getName(), page, size);
  }

  @GetMapping("/{id}")
  public ReconciliationRunResponse get(@PathVariable UUID id, Principal principal) {
    return reconciliationService.get(id, principal.getName());
  }

  @GetMapping("/{id}/summary")
  public ReconciliationSummaryResponse summary(@PathVariable UUID id, Principal principal) {
    return reconciliationService.summary(id, principal.getName());
  }

  @GetMapping("/{id}/discrepancies")
  public PageResponse<DiscrepancyResponse> discrepancies(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Principal principal) {
    return reconciliationService.discrepancies(id, principal.getName(), page, size);
  }
}

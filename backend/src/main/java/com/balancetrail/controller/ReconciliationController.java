package com.balancetrail.controller;

import com.balancetrail.dto.DiscrepancyResponse;
import com.balancetrail.dto.PageResponse;
import com.balancetrail.dto.ReconciliationRunResponse;
import com.balancetrail.dto.ReconciliationStartResponse;
import com.balancetrail.dto.ReconciliationSummaryResponse;
import com.balancetrail.dto.MatchSuggestionResponse;
import com.balancetrail.dto.SuggestionDecisionRequest;
import com.balancetrail.service.DiscrepancyExportService;
import com.balancetrail.service.MatchSuggestionService;
import com.balancetrail.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final DiscrepancyExportService exportService;
  private final MatchSuggestionService suggestionService;

  public ReconciliationController(
      ReconciliationService reconciliationService,
      DiscrepancyExportService exportService,
      MatchSuggestionService suggestionService) {
    this.reconciliationService = reconciliationService;
    this.exportService = exportService;
    this.suggestionService = suggestionService;
  }

  @Operation(summary = "Upload a CSV and start a reconciliation (formatId: a saved gateway format)")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ReconciliationStartResponse> create(
      @RequestPart("file") MultipartFile file,
      @RequestParam(required = false) Long formatId,
      Principal principal) {
    var response = reconciliationService.start(file, principal.getName(), formatId);
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

  @Operation(summary = "Download every discrepancy of a run as CSV")
  @GetMapping("/{id}/discrepancies/export")
  public void exportDiscrepancies(
      @PathVariable UUID id, Principal principal, HttpServletResponse response) throws IOException {
    // Check access before any CSV is written, so a wrong ID still gets a normal 404 response.
    exportService.checkAccess(id, principal.getName());
    response.setContentType("text/csv");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename("discrepancies-" + id + ".csv").build().toString());
    exportService.write(id, principal.getName(), response.getWriter());
  }

  @Operation(summary = "Suggested pairs: probably the same payment with a typo in the ID")
  @GetMapping("/{id}/suggestions")
  public PageResponse<MatchSuggestionResponse> suggestions(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Principal principal) {
    return suggestionService.list(id, principal.getName(), page, size);
  }

  @PostMapping("/{id}/suggestions/{suggestionId}/confirm")
  public MatchSuggestionResponse confirmSuggestion(
      @PathVariable UUID id, @PathVariable long suggestionId, Principal principal) {
    return suggestionService.confirm(id, suggestionId, principal.getName());
  }

  @PostMapping("/{id}/suggestions/{suggestionId}/reject")
  public MatchSuggestionResponse rejectSuggestion(
      @PathVariable UUID id,
      @PathVariable long suggestionId,
      @RequestBody(required = false) @jakarta.validation.Valid SuggestionDecisionRequest request,
      Principal principal) {
    return suggestionService.reject(
        id, suggestionId, principal.getName(), request == null ? null : request.note());
  }
}

package com.balancetrail.controller;

import com.balancetrail.dto.CreateGatewayFormatRequest;
import com.balancetrail.dto.GatewayFormatResponse;
import com.balancetrail.dto.MappingSuggestionResponse;
import com.balancetrail.service.GatewayFormatService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Saved column mappings for gateway files that don't use the standard 4-column layout. */
@RestController
@RequestMapping("/gateway-formats")
public class GatewayFormatController {
  private final GatewayFormatService formatService;

  public GatewayFormatController(GatewayFormatService formatService) {
    this.formatService = formatService;
  }

  @Operation(summary = "Suggest a column mapping for a sample file (nothing is saved)")
  @PostMapping(path = "/suggest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public MappingSuggestionResponse suggest(@RequestPart("file") MultipartFile file) {
    return formatService.suggest(file);
  }

  @Operation(summary = "Save a reviewed mapping as a new format (formats can't be edited)")
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<GatewayFormatResponse> create(
      @Valid @RequestBody CreateGatewayFormatRequest request, Principal principal) {
    var created = formatService.create(request, principal.getName());
    return ResponseEntity.created(URI.create("/gateway-formats/" + created.id())).body(created);
  }

  @GetMapping
  public List<GatewayFormatResponse> list() {
    return formatService.list();
  }
}

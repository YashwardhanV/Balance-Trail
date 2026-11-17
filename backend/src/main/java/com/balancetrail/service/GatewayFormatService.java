package com.balancetrail.service;

import com.balancetrail.batch.CsvColumns;
import com.balancetrail.batch.FieldRules;
import com.balancetrail.batch.MappedGatewayLineMapper;
import com.balancetrail.dto.CreateGatewayFormatRequest;
import com.balancetrail.dto.GatewayFormatResponse;
import com.balancetrail.dto.MappingSuggestionResponse;
import com.balancetrail.entity.GatewayFormatEntity;
import com.balancetrail.exception.ConflictException;
import com.balancetrail.exception.InvalidRequestException;
import com.balancetrail.exception.InvalidUploadException;
import com.balancetrail.exception.ResourceNotFoundException;
import com.balancetrail.mapping.AiColumnMappingSuggester;
import com.balancetrail.mapping.AiMappingException;
import com.balancetrail.mapping.ColumnMapping;
import com.balancetrail.mapping.ColumnMappingValidator;
import com.balancetrail.mapping.DateFormatDetector;
import com.balancetrail.mapping.DateFormats;
import com.balancetrail.mapping.HeaderAliasSuggester;
import com.balancetrail.mapping.SampleMasker;
import com.balancetrail.repository.AppUserRepository;
import com.balancetrail.repository.GatewayFormatRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Gateway formats: suggest a column mapping for a messy file, save it after the analyst checks
 * it, and read later files with it.
 *
 * <p>How a suggestion is made:
 *
 * <ol>
 *   <li>Header rules first ({@link HeaderAliasSuggester}): free, instant, nothing leaves the server.
 *   <li>Only if the rules can't place all four columns, and an AI service is configured, the AI is
 *       asked, with header names and MASKED sample rows only.
 *   <li>The AI's answer is validated against the real headers; anything invalid is dropped.
 *   <li>The date format is detected locally from the real sample values (never by the AI).
 *   <li>Nothing is saved until the analyst reviews the mapping and saves it.
 * </ol>
 */
@Service
public class GatewayFormatService {
  private static final Logger log = LoggerFactory.getLogger(GatewayFormatService.class);
  static final int SAMPLE_ROWS_READ = 20;
  static final int SAMPLE_ROWS_SENT_TO_AI = 5;
  private static final int MAX_COLUMNS = 60;
  private static final int MAX_HEADER_LENGTH = 100;

  private final FileStorageService fileStorageService;
  private final GatewayFormatRepository formatRepository;
  private final AppUserRepository userRepository;
  private final HeaderAliasSuggester headerRules;
  private final Optional<AiColumnMappingSuggester> ai;
  private final ObjectMapper objectMapper;

  public GatewayFormatService(
      FileStorageService fileStorageService,
      GatewayFormatRepository formatRepository,
      AppUserRepository userRepository,
      HeaderAliasSuggester headerRules,
      ObjectProvider<AiColumnMappingSuggester> ai,
      ObjectMapper objectMapper) {
    this.fileStorageService = fileStorageService;
    this.formatRepository = formatRepository;
    this.userRepository = userRepository;
    this.headerRules = headerRules;
    this.ai = Optional.ofNullable(ai.getIfAvailable());
    this.objectMapper = objectMapper;
  }

  private record Sample(List<String> headers, List<List<String>> rows) {}

  public MappingSuggestionResponse suggest(MultipartFile file) {
    fileStorageService.validateFile(file);
    Sample sample = readSample(file);
    List<String> headers = sample.headers();
    List<String> warnings = new ArrayList<>();

    // What an AI would see: header names (masked if they look like data) and masked values.
    List<String> headersForAi = headers.stream().map(SampleMasker::maskHeaderIfItLooksLikeData).toList();
    if (headers.stream().anyMatch(SampleMasker::looksLikeData)) {
      warnings.add("The first line looks like data, not column names. The file needs a header row.");
    }
    List<List<String>> maskedRows =
        sample.rows().stream()
            .limit(SAMPLE_ROWS_SENT_TO_AI)
            .map(row -> row.stream().map(SampleMasker::mask).toList())
            .toList();

    ColumnMapping mapping = headerRules.suggest(headers);
    boolean aiUsed = false;
    String suggestedBy = "HEADER_RULES";
    if (!mapping.isComplete()) {
      if (ai.isPresent()) {
        aiUsed = true;
        try {
          ColumnMapping fromAi = askAi(ai.get(), headers, headersForAi, maskedRows, warnings);
          ColumnMapping combined = mapping.fillMissingFrom(fromAi);
          if (!combined.equals(mapping)) {
            suggestedBy = ai.get().name();
          }
          mapping = combined;
        } catch (AiMappingException exception) {
          log.warn("AI column mapping failed, using header rules only: {}", exception.getMessage());
          warnings.add("The AI suggestion was not available (" + exception.getMessage() + "). Header rules were used.");
        }
      } else {
        warnings.add("Some columns could not be matched by name, and AI suggestions are off (no GEMINI_API_KEY).");
      }
    }
    mapping = ColumnMappingValidator.clean(mapping, headers).mapping();
    addMissingFieldWarnings(mapping, warnings);
    addAmountWarning(mapping, sample, warnings);

    List<String> dateCandidates = List.of();
    if (mapping.transactionDate() != null) {
      List<String> dates = column(sample, mapping.transactionDate());
      dateCandidates = DateFormatDetector.candidates(dates);
      if (!dateCandidates.isEmpty() && DateFormatDetector.failures(dates, dateCandidates.get(0)) > 0) {
        warnings.add(
            DateFormatDetector.failures(dates, dateCandidates.get(0)) + " sample date(s) don't fit "
                + dateCandidates.get(0) + "; those lines will be INVALID.");
      }
      if (dateCandidates.isEmpty()) {
        warnings.add("The sample dates fit none of the supported date formats. Pick the format yourself.");
      } else if (dateCandidates.size() > 1) {
        warnings.add(
            "The sample dates fit more than one format (" + String.join(", ", dateCandidates)
                + "). Check which one is right before saving.");
      }
    }

    return new MappingSuggestionResponse(
        headers,
        maskedRows,
        mapping.transactionId(),
        mapping.accountNumber(),
        mapping.amount(),
        mapping.transactionDate(),
        dateCandidates.isEmpty() ? null : dateCandidates.get(0),
        dateCandidates,
        DateFormats.all(),
        ai.isPresent(),
        aiUsed,
        suggestedBy,
        warnings);
  }

  /** Asks the AI, then keeps only answers that name a real header, translated back to its real name. */
  private ColumnMapping askAi(
      AiColumnMappingSuggester suggester,
      List<String> headers,
      List<String> headersForAi,
      List<List<String>> maskedRows,
      List<String> warnings) {
    ColumnMapping raw = suggester.suggest(headersForAi, maskedRows);
    if (raw == null) {
      throw new AiMappingException("the AI returned no mapping");
    }
    var checked = ColumnMappingValidator.clean(raw, headersForAi);
    if (!checked.problems().isEmpty()) {
      warnings.add("Parts of the AI suggestion were ignored: " + String.join("; ", checked.problems()));
    }
    ColumnMapping answer = checked.mapping();
    return new ColumnMapping(
        realHeader(answer.transactionId(), headers, headersForAi),
        realHeader(answer.accountNumber(), headers, headersForAi),
        realHeader(answer.amount(), headers, headersForAi),
        realHeader(answer.transactionDate(), headers, headersForAi));
  }

  private static String realHeader(String headerForAi, List<String> headers, List<String> headersForAi) {
    return headerForAi == null ? null : headers.get(headersForAi.indexOf(headerForAi));
  }

  private static void addMissingFieldWarnings(ColumnMapping mapping, List<String> warnings) {
    if (mapping.transactionId() == null) warnings.add("Pick the transaction ID column yourself.");
    if (mapping.accountNumber() == null) warnings.add("Pick the account number column yourself.");
    if (mapping.amount() == null) warnings.add("Pick the amount column yourself.");
    if (mapping.transactionDate() == null) warnings.add("Pick the transaction date column yourself.");
  }

  private static void addAmountWarning(ColumnMapping mapping, Sample sample, List<String> warnings) {
    if (mapping.amount() == null) {
      return;
    }
    column(sample, mapping.amount()).stream()
        .filter(value -> !value.isBlank() && FieldRules.parseAmount(value) == null)
        .findFirst()
        .ifPresent(
            value ->
                warnings.add(
                    "Some sample amounts are not plain numbers (for example \"" + SampleMasker.mask(value)
                        + "\"). Only plain numbers like 1250.00 are supported; such lines will be INVALID."));
  }

  private static List<String> column(Sample sample, String header) {
    int index = sample.headers().indexOf(header);
    return sample.rows().stream().filter(row -> row.size() > index).map(row -> row.get(index).trim()).toList();
  }

  private Sample readSample(MultipartFile file) {
    try (var reader =
        new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null || headerLine.isBlank()) {
        throw new InvalidUploadException("The file is empty");
      }
      if (headerLine.startsWith("﻿")) {
        headerLine = headerLine.substring(1);
      }
      List<String> headers = parseHeaders(headerLine);
      List<List<String>> rows = new ArrayList<>();
      String line;
      while (rows.size() < SAMPLE_ROWS_READ && (line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        try {
          List<String> row = CsvColumns.split(line);
          if (row.size() == headers.size()) {
            rows.add(row);
          }
        } catch (IllegalArgumentException ignored) {
          // A broken sample line is skipped; the real upload will report it as INVALID.
        }
      }
      if (rows.isEmpty()) {
        throw new InvalidUploadException("The file has no data rows with " + headers.size() + " columns");
      }
      return new Sample(headers, rows);
    } catch (IOException exception) {
      throw new InvalidUploadException("Could not read the uploaded CSV", exception);
    }
  }

  private static List<String> parseHeaders(String headerLine) {
    List<String> headers;
    try {
      headers = CsvColumns.split(headerLine).stream().map(String::trim).toList();
    } catch (IllegalArgumentException exception) {
      throw new InvalidUploadException("The header row is not valid CSV: " + exception.getMessage());
    }
    if (headers.size() < 4 || headers.size() > MAX_COLUMNS) {
      throw new InvalidUploadException("The file needs between 4 and " + MAX_COLUMNS + " columns");
    }
    Set<String> seen = new HashSet<>();
    for (String header : headers) {
      if (header.isEmpty() || header.length() > MAX_HEADER_LENGTH) {
        throw new InvalidUploadException("Every column needs a name of at most " + MAX_HEADER_LENGTH + " characters");
      }
      if (!seen.add(header.toLowerCase(Locale.ROOT))) {
        throw new InvalidUploadException("The column \"" + header + "\" appears twice in the header row");
      }
    }
    return headers;
  }

  /** Saves a reviewed mapping. Every rule is checked again here: the request may not come from our UI. */
  @Transactional
  public GatewayFormatResponse create(CreateGatewayFormatRequest request, String username) {
    String name = request.name().trim();
    if (formatRepository.existsByNameIgnoreCase(name)) {
      throw new ConflictException("A format named \"" + name + "\" already exists. Formats can't be edited; use a new name.");
    }
    List<String> headers = request.headers().stream().map(String::trim).toList();
    if (headers.size() < 4 || new HashSet<>(headers.stream().map(h -> h.toLowerCase(Locale.ROOT)).toList()).size() != headers.size()) {
      throw new InvalidRequestException("headers must hold at least 4 distinct column names");
    }
    var checked =
        ColumnMappingValidator.clean(
            new ColumnMapping(
                request.transactionIdColumn(),
                request.accountNumberColumn(),
                request.amountColumn(),
                request.transactionDateColumn()),
            headers);
    if (!checked.problems().isEmpty() || !checked.mapping().isComplete()) {
      throw new InvalidRequestException(
          checked.problems().isEmpty() ? "All four columns are required" : String.join("; ", checked.problems()));
    }
    if (!DateFormats.isAllowed(request.dateFormat())) {
      throw new InvalidRequestException("dateFormat must be one of: " + String.join(", ", DateFormats.all()));
    }
    var creator =
        userRepository
            .findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    ColumnMapping mapping = checked.mapping();
    var saved =
        formatRepository.save(
            new GatewayFormatEntity(
                name,
                toJson(headers),
                mapping.transactionId(),
                mapping.accountNumber(),
                mapping.amount(),
                mapping.transactionDate(),
                request.dateFormat(),
                request.suggestedBy() == null ? "MANUAL" : request.suggestedBy(),
                creator));
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<GatewayFormatResponse> list() {
    return formatRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public GatewayFormatEntity find(long id) {
    return formatRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Gateway format not found"));
  }

  /** The upload check for a file in this format: same columns, same order (names ignore case). */
  public Consumer<String> headerCheck(GatewayFormatEntity format) {
    List<String> expected = headers(format);
    return firstLine -> {
      List<String> actual;
      try {
        actual = firstLine == null ? List.of() : CsvColumns.split(firstLine).stream().map(String::trim).toList();
      } catch (IllegalArgumentException exception) {
        actual = List.of();
      }
      boolean same = actual.size() == expected.size();
      for (int index = 0; same && index < expected.size(); index++) {
        same = expected.get(index).equalsIgnoreCase(actual.get(index));
      }
      if (!same) {
        throw new InvalidUploadException(
            "This file's header does not match the format \"" + format.getName() + "\". Expected: "
                + String.join(",", expected));
      }
    };
  }

  /** The line reader for files in this format, used by the batch job. */
  public MappedGatewayLineMapper lineMapper(GatewayFormatEntity format) {
    List<String> headers = headers(format);
    return new MappedGatewayLineMapper(
        headers.size(),
        headers.indexOf(format.getTransactionIdColumn()),
        headers.indexOf(format.getAccountNumberColumn()),
        headers.indexOf(format.getAmountColumn()),
        headers.indexOf(format.getTransactionDateColumn()),
        format.getDateFormat());
  }

  private GatewayFormatResponse toResponse(GatewayFormatEntity format) {
    return new GatewayFormatResponse(
        format.getId(),
        format.getName(),
        headers(format),
        format.getTransactionIdColumn(),
        format.getAccountNumberColumn(),
        format.getAmountColumn(),
        format.getTransactionDateColumn(),
        format.getDateFormat(),
        format.getSuggestedBy(),
        format.getCreatedBy().getUsername(),
        format.getCreatedAt());
  }

  private List<String> headers(GatewayFormatEntity format) {
    try {
      return objectMapper.readValue(format.getHeadersJson(), new TypeReference<List<String>>() {});
    } catch (IOException exception) {
      throw new IllegalStateException("Stored headers of format " + format.getId() + " are not valid JSON", exception);
    }
  }

  private String toJson(List<String> headers) {
    try {
      String json = objectMapper.writeValueAsString(headers);
      if (json.length() > 4000) {
        throw new InvalidRequestException("The header row is too long to save");
      }
      return json;
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }
}

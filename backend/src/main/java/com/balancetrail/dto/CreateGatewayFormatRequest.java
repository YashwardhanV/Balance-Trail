package com.balancetrail.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * What the analyst saves after reviewing a suggestion. {@code headers} must be the header row of
 * the sample file; every column must be one of them.
 */
public record CreateGatewayFormatRequest(
    @NotBlank @Size(max = 80) String name,
    @NotEmpty @Size(max = 60) List<@NotBlank @Size(max = 100) String> headers,
    @NotBlank String transactionIdColumn,
    @NotBlank String accountNumberColumn,
    @NotBlank String amountColumn,
    @NotBlank String transactionDateColumn,
    @NotBlank String dateFormat,
    @Pattern(regexp = "GEMINI|HEADER_RULES|MANUAL", message = "suggestedBy must be GEMINI, HEADER_RULES or MANUAL")
        String suggestedBy) {}

package com.balancetrail.dto;

import java.util.List;

/**
 * A proposed mapping for the analyst to review. Nothing is saved yet.
 *
 * @param maskedSampleRows exactly what an AI service would see (shown so the analyst can check it)
 * @param aiEnabled whether an AI service is configured (GEMINI_API_KEY set)
 * @param aiUsed whether an AI service was actually called for this suggestion
 * @param suggestedBy GEMINI if the AI's answer was used, HEADER_RULES otherwise
 * @param dateFormatCandidates every allowed format the sample dates fit; more than one = ambiguous
 * @param warnings things the analyst should check before saving
 */
public record MappingSuggestionResponse(
    List<String> headers,
    List<List<String>> maskedSampleRows,
    String transactionIdColumn,
    String accountNumberColumn,
    String amountColumn,
    String transactionDateColumn,
    String dateFormat,
    List<String> dateFormatCandidates,
    List<String> allDateFormats,
    boolean aiEnabled,
    boolean aiUsed,
    String suggestedBy,
    List<String> warnings) {}

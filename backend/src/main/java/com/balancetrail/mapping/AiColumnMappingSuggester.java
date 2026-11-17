package com.balancetrail.mapping;

import java.util.List;

/**
 * An AI service that proposes a column mapping. It only ever receives header names and masked
 * sample rows (see {@link SampleMasker}), and its answer is always checked by
 * {@link ColumnMappingValidator} and then by the analyst before it is used.
 */
public interface AiColumnMappingSuggester {

  /** Short name stored on a saved format ("GEMINI"). */
  String name();

  /** Throws {@link AiMappingException} if the service fails or answers with something unusable. */
  ColumnMapping suggest(List<String> headers, List<List<String>> maskedRows);
}

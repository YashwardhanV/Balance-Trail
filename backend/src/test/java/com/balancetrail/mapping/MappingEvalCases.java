package com.balancetrail.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;

/**
 * A small, fixed set of realistic gateway headers with the right answer for each, kept in
 * src/test/resources/mapping-eval/cases.json. Used to measure the header rules (in every build)
 * and Gemini (only when GEMINI_API_KEY is set). Add a case whenever a real file is mapped wrongly.
 */
record MappingEvalCases(String name, boolean rulesCanDoIt, List<String> headers, List<String> expected) {

  ColumnMapping expectedMapping() {
    return new ColumnMapping(expected.get(0), expected.get(1), expected.get(2), expected.get(3));
  }

  static List<MappingEvalCases> load() throws IOException {
    try (var input = MappingEvalCases.class.getResourceAsStream("/mapping-eval/cases.json")) {
      return new ObjectMapper().readValue(input, new TypeReference<List<MappingEvalCases>>() {});
    }
  }
}

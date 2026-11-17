package com.balancetrail.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Calls the REAL Gemini API on the eval set and prints the accuracy. Skipped unless
 * GEMINI_API_KEY is set, so normal builds and CI never call Google or spend money.
 *
 * <p>Run it after changing the prompt or the model:
 * {@code GEMINI_API_KEY=... ./mvnw test -Dtest=GeminiEvalTest}
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiEvalTest {

  @Test
  void geminiMapsAtLeast90PercentOfFieldsCorrectly() throws Exception {
    var properties =
        new GeminiProperties(System.getenv("GEMINI_API_KEY"), System.getenv("GEMINI_MODEL"), null, Duration.ofSeconds(30));
    var gemini = new GeminiColumnMappingSuggester(RestClient.builder(), properties, new ObjectMapper());

    int correct = 0;
    int total = 0;
    for (var evalCase : MappingEvalCases.load()) {
      List<List<String>> noSamples = List.of();
      ColumnMapping got =
          ColumnMappingValidator.clean(gemini.suggest(evalCase.headers(), noSamples), evalCase.headers()).mapping();
      ColumnMapping expected = evalCase.expectedMapping();
      int caseCorrect =
          (expected.transactionId().equals(got.transactionId()) ? 1 : 0)
              + (expected.accountNumber().equals(got.accountNumber()) ? 1 : 0)
              + (expected.amount().equals(got.amount()) ? 1 : 0)
              + (expected.transactionDate().equals(got.transactionDate()) ? 1 : 0);
      System.out.printf("%-35s %d/4 %s%n", evalCase.name(), caseCorrect, got);
      correct += caseCorrect;
      total += 4;
    }
    System.out.printf("Gemini (%s): %d of %d fields correct%n", properties.model(), correct, total);
    assertThat((double) correct / total).isGreaterThanOrEqualTo(0.9);
  }
}

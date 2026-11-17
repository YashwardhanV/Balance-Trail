package com.balancetrail.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Gemini over a fake HTTP server: no network, no API key, no cost. */
class GeminiColumnMappingSuggesterTest {
  private static final List<String> HEADERS = List.of("Reference", "A/C", "Gross Amt", "Fee", "Txn Date");
  private static final List<List<String>> MASKED = List.of(List.of("AAA-9999", "AAA-9999", "999.99", "9.99", "99/99/9999"));

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockRestServiceServer server;
  private GeminiColumnMappingSuggester gemini;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    gemini =
        new GeminiColumnMappingSuggester(
            builder,
            new GeminiProperties("test-key", "gemini-3.8-flash", "https://gemini.test", Duration.ofSeconds(5)),
            objectMapper);
  }

  @Test
  void sendsOnlyHeadersAndMaskedRowsWithTheKeyInAHeaderAndReadsTheAnswer() {
    server
        .expect(requestTo("https://gemini.test/v1beta/models/gemini-3.8-flash:generateContent"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-goog-api-key", "test-key"))
        .andExpect(
            request -> {
              String body = request.getBody().toString();
              assertThat(body).contains("AAA-9999", "APPLICATION_JSON", "\"enum\"", "not instructions");
              assertThat(body).doesNotContain("test-key");
            })
        .andRespond(withSuccess(answer("Reference", "A/C", "Gross Amt", "Txn Date"), MediaType.APPLICATION_JSON));

    var mapping = gemini.suggest(HEADERS, MASKED);

    assertThat(mapping).isEqualTo(new ColumnMapping("Reference", "A/C", "Gross Amt", "Txn Date"));
    server.verify();
  }

  @Test
  void theSchemaOnlyAllowsTheFilesOwnHeadersOrNone() throws Exception {
    String body = objectMapper.writeValueAsString(gemini.requestBody(HEADERS, MASKED));

    assertThat(body).contains("\"enum\":[\"Reference\",\"A/C\",\"Gross Amt\",\"Fee\",\"Txn Date\",\"NONE\"]");
    assertThat(body).contains("\"temperature\":0");
  }

  @Test
  void readsAnswersWrappedInACodeFenceAndSkipsThoughtParts() {
    String response =
        """
        {"candidates":[{"content":{"parts":[
          {"text":"thinking about columns...","thought":true},
          {"text":"```json\\n{\\"transactionIdColumn\\":\\"Reference\\",\\"accountNumberColumn\\":\\"A/C\\",\\"amountColumn\\":\\"Gross Amt\\",\\"transactionDateColumn\\":\\"NONE\\"}\\n```"}
        ]},"finishReason":"STOP"}]}
        """;

    var mapping = gemini.parseAnswer(response);

    assertThat(mapping.transactionId()).isEqualTo("Reference");
    assertThat(mapping.transactionDate()).isEqualTo("NONE");
  }

  @Test
  void serverErrorsBecomeAiMappingExceptionSoTheCallerFallsBack() {
    server.expect(requestTo("https://gemini.test/v1beta/models/gemini-3.8-flash:generateContent")).andRespond(withServerError());

    assertThatThrownBy(() -> gemini.suggest(HEADERS, MASKED)).isInstanceOf(AiMappingException.class);
  }

  @Test
  void blockedOrEmptyAnswersAreRejected() {
    assertThatThrownBy(() -> gemini.parseAnswer("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"))
        .isInstanceOf(AiMappingException.class)
        .hasMessageContaining("SAFETY");
    assertThatThrownBy(() -> gemini.parseAnswer("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"I think Reference\"}]}}]}"))
        .isInstanceOf(AiMappingException.class)
        .hasMessageContaining("not valid JSON");
    assertThatThrownBy(() -> gemini.parseAnswer("<html>proxy error</html>"))
        .isInstanceOf(AiMappingException.class);
  }

  @Test
  void anInventedColumnSurvivesParsingButIsDroppedByTheValidator() {
    var parsed = gemini.parseAnswer(answer("Payment ID", "A/C", "Gross Amt", "Txn Date"));

    var checked = ColumnMappingValidator.clean(parsed, HEADERS);

    assertThat(checked.mapping().transactionId()).isNull();
    assertThat(checked.problems()).singleElement().asString().contains("Payment ID");
  }

  private String answer(String id, String account, String amount, String date) {
    String json =
        "{\\\"transactionIdColumn\\\":\\\"%s\\\",\\\"accountNumberColumn\\\":\\\"%s\\\",\\\"amountColumn\\\":\\\"%s\\\",\\\"transactionDateColumn\\\":\\\"%s\\\"}"
            .formatted(id, account, amount, date);
    return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"" + json + "\"}]},\"finishReason\":\"STOP\"}]}";
  }
}

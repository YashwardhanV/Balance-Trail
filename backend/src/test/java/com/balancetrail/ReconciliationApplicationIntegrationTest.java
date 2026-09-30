package com.balancetrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationApplicationIntegrationTest {
  private static final String USERNAME = "integration-analyst";
  private static final String PASSWORD = "integration-password";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("balancetrail_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.bootstrap-user.username", () -> USERNAME);
    registry.add("app.bootstrap-user.password", () -> PASSWORD);
    registry.add("app.storage.upload-directory", () -> "target/test-uploads");
    registry.add("app.batch.initial-retry-delay-ms", () -> "1");
    registry.add("app.batch.max-retry-delay-ms", () -> "2");
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void authenticationIsRequiredAndValidCredentialsReturnTheCurrentUser() throws Exception {
    mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(get("/auth/me").with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(USERNAME))
        .andExpect(jsonPath("$.role").value("ANALYST"));
  }

  @Test
  void validAndInvalidRowsAreIsolatedAndRepeatedInputIsIdempotent() throws Exception {
    String csv =
        """
        transaction_id,account_number,amount,transaction_date
        TXN-1001,ACC-1001,1250.00,2026-07-01
        TXN-1002,ACC-1002,500.00,2026-07-01
        TXN-NOT-IN-LEDGER,ACC-404,75.00,2026-07-01
        TXN-1001,ACC-1001,1250.00,2026-07-01
        TXN-BAD-AMOUNT,ACC-500,not-a-number,2026-07-01
        """;

    JsonNode first = upload("mixed-records.csv", csv, 202);
    String runId = first.at("/reconciliation/id").asText();
    JsonNode completed = awaitTerminal(runId);

    assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(completed.get("totalCount").asLong()).isEqualTo(5);
    assertThat(completed.get("matchedCount").asLong()).isEqualTo(1);
    assertThat(completed.get("amountMismatchCount").asLong()).isEqualTo(1);
    assertThat(completed.get("missingCount").asLong()).isEqualTo(1);
    assertThat(completed.get("invalidCount").asLong()).isEqualTo(1);
    assertThat(completed.get("duplicateCount").asLong()).isEqualTo(1);

    mockMvc
        .perform(
            get("/reconciliations/{id}/summary", runId)
                .with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invalidCount").value(1))
        .andExpect(jsonPath("$.duplicateCount").value(1));
    mockMvc
        .perform(
            get("/reconciliations/{id}/discrepancies", runId)
                .with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4));

    JsonNode replay = upload("renamed-copy.csv", csv, 200);
    assertThat(replay.get("idempotentReplay").asBoolean()).isTrue();
    assertThat(replay.at("/reconciliation/id").asText()).isEqualTo(runId);
  }

  @Test
  void malformedCsvRowsAreRecordedWithoutRollingBackValidRows() throws Exception {
    String csv =
        """
        transaction_id,account_number,amount,transaction_date
        TXN-1003,ACC-1003,3200.00,2026-07-01
        TXN-TOO-FEW,ACC-1,10.00
        "TXN-UNCLOSED,ACC-2,20.00,2026-07-01
        """;

    JsonNode created = upload("malformed-lines.csv", csv, 202);
    JsonNode completed = awaitTerminal(created.at("/reconciliation/id").asText());

    assertThat(completed.get("totalCount").asLong()).isEqualTo(3);
    assertThat(completed.get("matchedCount").asLong()).isEqualTo(1);
    assertThat(completed.get("invalidCount").asLong()).isEqualTo(2);
  }

  @Test
  void invalidHeaderReturnsBadRequestWithoutCreatingARun() throws Exception {
    var file =
        new MockMultipartFile(
            "file", "wrong.csv", "text/csv", "id,amount\nTX-1,10.00\n".getBytes());

    mockMvc
        .perform(
            multipart("/reconciliations")
                .file(file)
                .with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CSV header")));
  }

  @Test
  void databaseRejectsDuplicateLedgerIdsAndNonPositiveAmounts() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO ledger_transaction "
                        + "(transaction_ref, account_number, amount, transaction_date) "
                        + "VALUES ('TXN-1001', 'ACC-X', 1.00, DATE '2026-08-01')"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO ledger_transaction "
                        + "(transaction_ref, account_number, amount, transaction_date) "
                        + "VALUES ('TXN-NEGATIVE', 'ACC-X', -1.00, DATE '2026-08-01')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private JsonNode upload(String fileName, String contents, int expectedStatus) throws Exception {
    var file =
        new MockMultipartFile("file", fileName, "text/csv", contents.getBytes());
    String body =
        mockMvc
            .perform(
                multipart("/reconciliations")
                    .file(file)
                    .with(httpBasic(USERNAME, PASSWORD)))
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private JsonNode awaitTerminal(String runId) throws Exception {
    for (int attempt = 0; attempt < 200; attempt++) {
      String body =
          mockMvc
              .perform(
                  get("/reconciliations/{id}", runId)
                      .with(httpBasic(USERNAME, PASSWORD)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      JsonNode run = objectMapper.readTree(body);
      if (run.get("status").asText().matches("COMPLETED|FAILED")) {
        return run;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("Reconciliation did not complete within 10 seconds");
  }
}

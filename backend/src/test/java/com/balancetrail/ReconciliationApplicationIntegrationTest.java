package com.balancetrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.balancetrail.config.InterruptedWorkRecovery;
import com.balancetrail.mapping.AiColumnMappingSuggester;
import com.balancetrail.mapping.AiMappingException;
import com.balancetrail.mapping.ColumnMapping;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired InterruptedWorkRecovery interruptedWorkRecovery;

  /** Stands in for Gemini, so these tests never call Google. */
  @MockitoBean AiColumnMappingSuggester ai;

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
    // The seeded ledger has 8 rows dated 2026-07-01; the file mentions only TXN-1001 and TXN-1002.
    assertThat(completed.get("missingInGatewayCount").asLong()).isEqualTo(6);

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
        .andExpect(jsonPath("$.totalElements").value(4 + 6));

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
  void accountAndDateDifferencesAndLedgerOnlyRowsAreReported() throws Exception {
    // Seeded ledger: TXN-1005 ACC-1005 700.00, TXN-1006 ACC-1006 1999.00, TXN-1007 ACC-1007 42.25,
    // all dated 2026-07-01. Date tolerance is 2 days.
    String csv =
        """
        transaction_id,account_number,amount,transaction_date
        TXN-1005,ACC-WRONG,700.00,2026-07-01
        TXN-1006,ACC-1006,1999.00,2026-07-05
        TXN-1007,ACC-1007,42.25,2026-07-02
        """;

    JsonNode created = upload("account-and-date.csv", csv, 202);
    String runId = created.at("/reconciliation/id").asText();
    JsonNode completed = awaitTerminal(runId);

    assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(completed.get("totalCount").asLong()).isEqualTo(3);
    assertThat(completed.get("accountMismatchCount").asLong()).isEqualTo(1);
    assertThat(completed.get("dateMismatchCount").asLong()).isEqualTo(1);
    assertThat(completed.get("matchedCount").asLong()).isEqualTo(1);
    // Window 2026-07-01..2026-07-05: TXN-1001, 1002, 1003, 1004 and 1008 are not in the file.
    assertThat(completed.get("missingInGatewayCount").asLong()).isEqualTo(5);

    JsonNode page =
        objectMapper.readTree(
            mockMvc
                .perform(
                    get("/reconciliations/{id}/discrepancies", runId)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode rows = page.get("content");
    assertThat(page.get("totalElements").asLong()).isEqualTo(7);
    assertThat(rows.get(0).get("status").asText()).isEqualTo("ACCOUNT_MISMATCH");
    assertThat(rows.get(0).get("ledgerAccountNumber").asText()).isEqualTo("ACC-1005");
    assertThat(rows.get(1).get("status").asText()).isEqualTo("DATE_MISMATCH");
    // Ledger-only rows come after the file's lines and have no line number.
    assertThat(rows.get(2).get("status").asText()).isEqualTo("MISSING_IN_GATEWAY");
    assertThat(rows.get(2).get("lineNumber").isNull()).isTrue();
    assertThat(rows.get(2).get("ledgerTransactionId").asText()).isEqualTo("TXN-1001");

    String export =
        mockMvc
            .perform(
                get("/reconciliations/{id}/discrepancies/export", runId)
                    .with(httpBasic(USERNAME, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                    .string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String[] lines = export.split("\r\n");
    assertThat(lines).hasSize(1 + 7);
    assertThat(lines[0]).startsWith("line_number,status,");
    assertThat(lines[1]).startsWith("2,ACCOUNT_MISMATCH,TXN-1005,ACC-WRONG,700.00,2026-07-01,TXN-1005,ACC-1005");
    assertThat(lines[3]).startsWith(",MISSING_IN_GATEWAY,,,,,TXN-1001,ACC-1001,1250.00,2026-07-01");
  }

  @Test
  void exportOfSomeoneElsesRunIsNotFound() throws Exception {
    mockMvc
        .perform(
            get("/reconciliations/{id}/discrepancies/export", java.util.UUID.randomUUID())
                .with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(status().isNotFound());
  }

  @Test
  void failedRunCanBeRetriedByUploadingTheSameFileAgain() throws Exception {
    String csv =
        """
        transaction_id,account_number,amount,transaction_date
        TXN-1004,ACC-1004,85.50,2026-07-01
        """;
    String firstId = upload("retry-me.csv", csv, 202).at("/reconciliation/id").asText();
    awaitTerminal(firstId);
    jdbcTemplate.update("UPDATE reconciliation_run SET status = 'FAILED' WHERE id = ?::uuid", firstId);

    JsonNode retry = upload("retry-me.csv", csv, 202);

    assertThat(retry.get("idempotentReplay").asBoolean()).isFalse();
    String retryId = retry.at("/reconciliation/id").asText();
    assertThat(retryId).isNotEqualTo(firstId);
    assertThat(awaitTerminal(retryId).get("matchedCount").asLong()).isEqualTo(1);
  }

  @Test
  void ledgerImportUpdatesTheLedgerAndTheSameFileIsReprocessedAgainstTheNewVersion()
      throws Exception {
    String gatewayCsv =
        """
        transaction_id,account_number,amount,transaction_date
        LG-1,ACC-1,100.00,2030-01-10
        LG-2,ACC-2,50.00,2030-01-10
        """;
    String beforeId = upload("ledger-test-gateway.csv", gatewayCsv, 202).at("/reconciliation/id").asText();
    JsonNode before = awaitTerminal(beforeId);
    assertThat(before.get("missingCount").asLong()).isEqualTo(2);

    String ledgerCsv =
        """
        transaction_id,account_number,amount,transaction_date,description
        LG-1,ACC-1,100.00,2030-01-10,Card settlement
        LG-2,ACC-2,55.00,2030-01-10,
        LG-3,ACC-3,10.00,2030-01-10,"Wallet, refund"
        LG-4,ACC-4,not-a-number,2030-01-10,
        LG-1,ACC-1,100.00,2030-01-10,second copy
        """;
    JsonNode ledgerImport = uploadLedger(ledgerCsv, 202);
    long importId = ledgerImport.get("id").asLong();
    JsonNode imported = awaitLedgerImport(importId);
    assertThat(imported.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(imported.get("totalCount").asLong()).isEqualTo(5);
    assertThat(imported.get("importedCount").asLong()).isEqualTo(3);
    assertThat(imported.get("invalidCount").asLong()).isEqualTo(2);
    mockMvc
        .perform(get("/ledger-imports/{id}/errors", importId).with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].lineNumber").value(5))
        .andExpect(jsonPath("$.content[1].reason").value(org.hamcrest.Matchers.containsString("already appeared")));

    // Same gateway bytes, but the ledger changed: a new run, not a replay of the stale one.
    JsonNode after = upload("ledger-test-gateway.csv", gatewayCsv, 202);
    assertThat(after.get("idempotentReplay").asBoolean()).isFalse();
    assertThat(after.at("/reconciliation/ledgerVersion").asLong()).isEqualTo(importId);
    JsonNode afterRun = awaitTerminal(after.at("/reconciliation/id").asText());
    assertThat(afterRun.get("matchedCount").asLong()).isEqualTo(1);
    assertThat(afterRun.get("amountMismatchCount").asLong()).isEqualTo(1);
    assertThat(afterRun.get("missingInGatewayCount").asLong()).isEqualTo(1);

    // Nothing changed since: now it is a replay.
    assertThat(upload("ledger-test-gateway.csv", gatewayCsv, 200).get("idempotentReplay").asBoolean())
        .isTrue();

    // A corrected ledger updates existing rows instead of failing on the duplicate key.
    awaitLedgerImport(
        uploadLedger(
                "transaction_id,account_number,amount,transaction_date,description\n"
                    + "LG-2,ACC-2,50.00,2030-01-10,corrected\n",
                202)
            .get("id")
            .asLong());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT amount FROM ledger_transaction WHERE transaction_ref = 'LG-2'",
                java.math.BigDecimal.class))
        .isEqualByComparingTo("50.00");
  }

  @Test
  void ledgerFileWithTheWrongHeaderIsRejected() throws Exception {
    uploadLedger("transaction_id,account_number,amount,transaction_date\nX,Y,1.00,2030-01-01\n", 400);
  }

  @Test
  void ledgerImportAndReconciliationDoNotRunAtTheSameTime() throws Exception {
    String csv =
        """
        transaction_id,account_number,amount,transaction_date
        TXN-1008,ACC-1008,910.10,2026-07-01
        """;
    String runId = upload("busy-check.csv", csv, 202).at("/reconciliation/id").asText();
    awaitTerminal(runId);

    jdbcTemplate.update("UPDATE reconciliation_run SET status = 'RUNNING' WHERE id = ?::uuid", runId);
    try {
      uploadLedger("transaction_id,account_number,amount,transaction_date,description\n", 409);
    } finally {
      jdbcTemplate.update("UPDATE reconciliation_run SET status = 'COMPLETED' WHERE id = ?::uuid", runId);
    }

    Long userId =
        jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, USERNAME);
    jdbcTemplate.update(
        "INSERT INTO ledger_import (id, uploaded_by, original_file_name, file_sha256, stored_file_path,"
            + " status, created_at) VALUES (-1, ?, 'x.csv', 'x', 'x', 'RUNNING', now())",
        userId);
    try {
      upload(
          "while-ledger-busy.csv",
          "transaction_id,account_number,amount,transaction_date\nTXN-1008,ACC-1008,910.10,2026-07-01\n\n",
          409);
    } finally {
      jdbcTemplate.update("DELETE FROM ledger_import WHERE id = -1");
    }
  }

  @Test
  void runsLeftUnfinishedByARestartAreMarkedFailed() throws Exception {
    String csv =
        """
        transaction_id,account_number,amount,transaction_date
        TXN-1002,ACC-1002,499.99,2026-07-01
        """;
    String runId = upload("interrupted.csv", csv, 202).at("/reconciliation/id").asText();
    awaitTerminal(runId);
    jdbcTemplate.update("UPDATE reconciliation_run SET status = 'RUNNING' WHERE id = ?::uuid", runId);

    assertThat(interruptedWorkRecovery.recover()).isEqualTo(1);

    JsonNode run = awaitTerminal(runId);
    assertThat(run.get("status").asText()).isEqualTo("FAILED");
    assertThat(run.get("failureMessage").asText()).contains("Server stopped");
  }

  @Test
  void aTypoInTheIdBecomesASuggestedPairThatCanBeConfirmedOnce() throws Exception {
    // Seeded ledger: TXN-1008 ACC-1008 910.10 2026-07-01. The file has it as TXN-10088.
    String csv =
        """
        transaction_id,account_number,amount,transaction_date
        TXN-10088,ACC-1008,910.10,2026-07-01
        TXN-1001,ACC-1001,1250.00,2026-07-01
        """;
    String runId = upload("typo-pair.csv", csv, 202).at("/reconciliation/id").asText();
    JsonNode run = awaitTerminal(runId);
    assertThat(run.get("missingCount").asLong()).isEqualTo(1);
    assertThat(run.get("suggestedPairCount").asLong()).isEqualTo(1);

    JsonNode suggestion = suggestions(runId).get("content").get(0);
    assertThat(suggestion.get("gatewayTransactionId").asText()).isEqualTo("TXN-10088");
    assertThat(suggestion.get("ledgerTransactionId").asText()).isEqualTo("TXN-1008");
    assertThat(suggestion.get("gatewayLineNumber").asLong()).isEqualTo(2);
    assertThat(suggestion.get("idDistance").asInt()).isEqualTo(1);
    assertThat(suggestion.get("ambiguous").asBoolean()).isFalse();
    assertThat(suggestion.get("status").asText()).isEqualTo("PENDING");
    long suggestionId = suggestion.get("id").asLong();

    mockMvc
        .perform(post("/reconciliations/{id}/suggestions/{s}/confirm", runId, suggestionId).with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.decidedBy").value(USERNAME));
    mockMvc
        .perform(post("/reconciliations/{id}/suggestions/{s}/confirm", runId, suggestionId).with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(status().isConflict());

    // The original results are not changed by the decision (audit trail).
    mockMvc
        .perform(get("/reconciliations/{id}", runId).with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(jsonPath("$.missingCount").value(1));
  }

  @Test
  void ambiguousPairsAreLeftToTheAnalystAndClosedWhenOneIsConfirmed() throws Exception {
    String csv =
        """
        transaction_id,account_number,amount,transaction_date
        TXN-10081,ACC-1008,910.10,2026-07-01
        TXN-10082,ACC-1008,910.10,2026-07-01
        TXN-10083,ACC-1008,910.10,2026-07-01
        """;
    String runId = upload("ambiguous-pairs.csv", csv, 202).at("/reconciliation/id").asText();
    assertThat(awaitTerminal(runId).get("suggestedPairCount").asLong()).isEqualTo(3);
    JsonNode content = suggestions(runId).get("content");
    for (JsonNode suggestion : content) {
      assertThat(suggestion.get("ambiguous").asBoolean()).isTrue();
    }

    mockMvc
        .perform(
            post("/reconciliations/{id}/suggestions/{s}/reject", runId, content.get(0).get("id").asLong())
                .with(httpBasic(USERNAME, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Different customer\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.decisionNote").value("Different customer"));
    mockMvc
        .perform(
            post("/reconciliations/{id}/suggestions/{s}/confirm", runId, content.get(1).get("id").asLong())
                .with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(status().isOk());

    JsonNode after = suggestions(runId).get("content");
    assertThat(after.get(0).get("decisionNote").asText()).isEqualTo("Different customer");
    assertThat(after.get(1).get("status").asText()).isEqualTo("CONFIRMED");
    assertThat(after.get(2).get("status").asText()).isEqualTo("REJECTED");
    assertThat(after.get(2).get("decisionNote").asText()).contains("another pair was confirmed");
  }

  @Test
  void aMessyGatewayFileIsMappedByHeaderRulesSavedAsAFormatAndReconciled() throws Exception {
    String messy =
        """
        Txn Ref,Customer Email,Acct No,Amount (INR),Settlement Dt,Status
        TXN-1001,ravi@example.in,ACC-1001,1250.00,01/07/2026,SUCCESS
        TXN-1002,meera@example.in,ACC-1002,499.99,01/07/2026,SUCCESS
        TXN-1003,arjun@example.in,ACC-1003,3200.00,15/07/2026,SUCCESS
        TXN-1004,dev@example.in,ACC-1004,85.50,2026-07-01,SUCCESS
        """;

    JsonNode suggestion = suggestMapping("messy.csv", messy);
    assertThat(suggestion.get("suggestedBy").asText()).isEqualTo("HEADER_RULES");
    assertThat(suggestion.get("aiUsed").asBoolean()).isFalse();
    assertThat(suggestion.get("transactionIdColumn").asText()).isEqualTo("Txn Ref");
    assertThat(suggestion.get("accountNumberColumn").asText()).isEqualTo("Acct No");
    assertThat(suggestion.get("amountColumn").asText()).isEqualTo("Amount (INR)");
    assertThat(suggestion.get("transactionDateColumn").asText()).isEqualTo("Settlement Dt");
    assertThat(suggestion.get("dateFormat").asText()).isEqualTo("dd/MM/yyyy");
    assertThat(suggestion.get("maskedSampleRows").toString()).doesNotContain("1001", "ravi", "1250").contains("AAA-9999");
    assertThat(suggestion.get("warnings").toString()).contains("1 sample date(s) don't fit dd/MM/yyyy");

    String request =
        """
        {"name":"Settlement report v1","headers":["Txn Ref","Customer Email","Acct No","Amount (INR)","Settlement Dt","Status"],
         "transactionIdColumn":"Txn Ref","accountNumberColumn":"Acct No","amountColumn":"Amount (INR)",
         "transactionDateColumn":"Settlement Dt","dateFormat":"dd/MM/yyyy","suggestedBy":"HEADER_RULES"}
        """;
    long formatId = objectMapper.readTree(createFormat(request, 201)).get("id").asLong();
    createFormat(request.replace("Settlement report v1", "SETTLEMENT REPORT V1"), 409);
    createFormat(request.replace("Settlement report v1", "Bad mapping").replace("\"amountColumn\":\"Amount (INR)\"", "\"amountColumn\":\"Net\""), 400);
    createFormat(request.replace("Settlement report v1", "Bad date").replace("dd/MM/yyyy", "dd MMMM yy"), 400);

    JsonNode created = uploadWithFormat("messy.csv", messy, formatId, 202);
    String runId = created.at("/reconciliation/id").asText();
    assertThat(created.at("/reconciliation/gatewayFormatId").asLong()).isEqualTo(formatId);
    JsonNode run = awaitTerminal(runId);
    assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.get("totalCount").asLong()).isEqualTo(4);
    assertThat(run.get("matchedCount").asLong()).isEqualTo(2);
    assertThat(run.get("dateMismatchCount").asLong()).isEqualTo(1);
    assertThat(run.get("invalidCount").asLong()).isEqualTo(1);

    // Line numbers are the lines of the uploaded file (header = line 1).
    mockMvc
        .perform(get("/reconciliations/{id}/discrepancies", runId).with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(jsonPath("$.content[0].lineNumber").value(4))
        .andExpect(jsonPath("$.content[0].status").value("DATE_MISMATCH"))
        .andExpect(jsonPath("$.content[1].lineNumber").value(5))
        .andExpect(jsonPath("$.content[1].status").value("INVALID"))
        .andExpect(jsonPath("$.content[1].reason").value("Transaction date must use dd/MM/yyyy"));

    // Same file + same format again: replay. Same file without the format: wrong header.
    assertThat(uploadWithFormat("messy.csv", messy, formatId, 200).get("idempotentReplay").asBoolean()).isTrue();
    upload("messy.csv", messy, 400);
    uploadWithFormat("other.csv", "transaction_id,account_number,amount,transaction_date\nX,Y,1.00,2026-07-01\n", formatId, 400);
    uploadWithFormat("messy.csv", messy, 999_999, 404);

    mockMvc
        .perform(get("/gateway-formats").with(httpBasic(USERNAME, PASSWORD)))
        .andExpect(jsonPath("$[?(@.name == 'Settlement report v1')].dateFormat").value("dd/MM/yyyy"));
  }

  @Test
  void whenRulesCantPlaceEveryColumnTheAiIsAskedWithMaskedSamplesOnly() throws Exception {
    reset(ai);
    when(ai.name()).thenReturn("GEMINI");
    when(ai.suggest(any(), any())).thenReturn(new ColumnMapping("Reference", "A/C", "Gross Amt", "Txn Date"));
    String messy =
        """
        Reference,A/C,Gross Amt,Fee,Net Amt,Txn Date
        PAY-77001,50100234567,1250.00,10.00,1240.00,01/07/2026
        PAY-77002,50100234568,499.99,4.00,495.99,13/07/2026
        """;

    JsonNode suggestion = suggestMapping("hard.csv", messy);

    assertThat(suggestion.get("aiUsed").asBoolean()).isTrue();
    assertThat(suggestion.get("suggestedBy").asText()).isEqualTo("GEMINI");
    assertThat(suggestion.get("amountColumn").asText()).isEqualTo("Gross Amt");
    assertThat(suggestion.get("dateFormat").asText()).isEqualTo("dd/MM/yyyy");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<List<String>>> rows = ArgumentCaptor.forClass(List.class);
    verify(ai).suggest(any(), rows.capture());
    assertThat(rows.getValue().toString()).doesNotContain("50100234567", "1250", "PAY-77001").contains("99999999999");
  }

  @Test
  void ifTheAiFailsTheHeaderRulesAnswerAndTheAnalystIsTold() throws Exception {
    reset(ai);
    when(ai.name()).thenReturn("GEMINI");
    when(ai.suggest(any(), any())).thenThrow(new AiMappingException("Gemini request failed: 503"));

    JsonNode suggestion =
        suggestMapping("hard2.csv", "Reference,A/C,Gross Amt,Txn Date\nPAY-1,ACC-1,10.00,2026-07-01\n");

    assertThat(suggestion.get("suggestedBy").asText()).isEqualTo("HEADER_RULES");
    assertThat(suggestion.get("transactionDateColumn").asText()).isEqualTo("Txn Date");
    assertThat(suggestion.get("transactionIdColumn").isNull()).isTrue();
    assertThat(suggestion.get("warnings").toString()).contains("not available", "Pick the transaction ID column yourself");
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

  private JsonNode uploadLedger(String contents, int expectedStatus) throws Exception {
    var file = new MockMultipartFile("file", "ledger.csv", "text/csv", contents.getBytes());
    String body =
        mockMvc
            .perform(multipart("/ledger-imports").file(file).with(httpBasic(USERNAME, PASSWORD)))
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private JsonNode awaitLedgerImport(long importId) throws Exception {
    for (int attempt = 0; attempt < 200; attempt++) {
      JsonNode ledgerImport =
          objectMapper.readTree(
              mockMvc
                  .perform(get("/ledger-imports/{id}", importId).with(httpBasic(USERNAME, PASSWORD)))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString());
      if (ledgerImport.get("status").asText().matches("COMPLETED|FAILED")) {
        return ledgerImport;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("Ledger import did not complete within 10 seconds");
  }

  private JsonNode suggestions(String runId) throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(get("/reconciliations/{id}/suggestions", runId).with(httpBasic(USERNAME, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode suggestMapping(String fileName, String contents) throws Exception {
    var file = new MockMultipartFile("file", fileName, "text/csv", contents.getBytes());
    return objectMapper.readTree(
        mockMvc
            .perform(multipart("/gateway-formats/suggest").file(file).with(httpBasic(USERNAME, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String createFormat(String json, int expectedStatus) throws Exception {
    return mockMvc
        .perform(
            post("/gateway-formats")
                .with(httpBasic(USERNAME, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().is(expectedStatus))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private JsonNode uploadWithFormat(String fileName, String contents, long formatId, int expectedStatus)
      throws Exception {
    var file = new MockMultipartFile("file", fileName, "text/csv", contents.getBytes());
    String body =
        mockMvc
            .perform(
                multipart("/reconciliations")
                    .file(file)
                    .param("formatId", String.valueOf(formatId))
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

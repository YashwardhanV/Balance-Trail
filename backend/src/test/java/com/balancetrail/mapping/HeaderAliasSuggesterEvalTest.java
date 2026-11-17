package com.balancetrail.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Header rules against the eval set. Runs in every build. */
class HeaderAliasSuggesterEvalTest {
  private final HeaderAliasSuggester rules = new HeaderAliasSuggester();

  @Test
  void rulesMapTheCommonLayoutsExactly() throws Exception {
    for (var evalCase : MappingEvalCases.load()) {
      if (evalCase.rulesCanDoIt()) {
        assertThat(rules.suggest(evalCase.headers())).as(evalCase.name()).isEqualTo(evalCase.expectedMapping());
      }
    }
  }

  @Test
  void rulesNeverGuessWrongTheyOnlyLeaveFieldsEmpty() throws Exception {
    for (var evalCase : MappingEvalCases.load()) {
      ColumnMapping got = rules.suggest(evalCase.headers());
      ColumnMapping expected = evalCase.expectedMapping();
      List<String[]> fields =
          List.of(
              new String[] {got.transactionId(), expected.transactionId()},
              new String[] {got.accountNumber(), expected.accountNumber()},
              new String[] {got.amount(), expected.amount()},
              new String[] {got.transactionDate(), expected.transactionDate()});
      for (String[] field : fields) {
        if (field[0] != null) {
          assertThat(field[0]).as(evalCase.name()).isEqualTo(field[1]);
        }
      }
    }
  }

  @Test
  void theHardCasesReallyNeedMoreThanRules() throws Exception {
    for (var evalCase : MappingEvalCases.load()) {
      if (!evalCase.rulesCanDoIt()) {
        assertThat(rules.suggest(evalCase.headers()).isComplete()).as(evalCase.name()).isFalse();
      }
    }
  }
}

package com.balancetrail.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CsvTextTest {

  @Test
  void plainValuesAreWrittenAsTheyAre() {
    assertThat(CsvText.cell("TX-1")).isEqualTo("TX-1");
    assertThat(CsvText.cell(new BigDecimal("10.50"))).isEqualTo("10.50");
    assertThat(CsvText.cell(null)).isEmpty();
  }

  @Test
  void commasQuotesAndLineBreaksAreQuoted() {
    assertThat(CsvText.cell("a,b")).isEqualTo("\"a,b\"");
    assertThat(CsvText.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    assertThat(CsvText.cell("two\nlines")).isEqualTo("\"two\nlines\"");
  }

  @Test
  void textThatASpreadsheetWouldRunAsAFormulaIsDefused() {
    assertThat(CsvText.cell("=HYPERLINK(\"x\")")).startsWith("\"'=HYPERLINK");
    assertThat(CsvText.cell("+1")).isEqualTo("'+1");
    assertThat(CsvText.cell("@SUM")).isEqualTo("'@SUM");
  }
}

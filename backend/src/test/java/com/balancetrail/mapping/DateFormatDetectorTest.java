package com.balancetrail.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DateFormatDetectorTest {

  @Test
  void aDayAbove12SettlesDayFirstVersusMonthFirst() {
    assertThat(DateFormatDetector.candidates(List.of("01/07/2026", "15/07/2026"))).containsExactly("dd/MM/yyyy");
    assertThat(DateFormatDetector.candidates(List.of("07/01/2026", "07/15/2026"))).containsExactly("MM/dd/yyyy");
  }

  @Test
  void onlySmallDaysAreAmbiguousAndDayFirstIsProposedFirst() {
    assertThat(DateFormatDetector.candidates(List.of("01/07/2026", "05/07/2026")))
        .containsExactly("dd/MM/yyyy", "MM/dd/yyyy");
  }

  @Test
  void recognisesTheCommonGatewayLayouts() {
    assertThat(DateFormatDetector.candidates(List.of("2026-07-09"))).containsExactly("yyyy-MM-dd");
    assertThat(DateFormatDetector.candidates(List.of("09-Jul-2026", "13-JUL-2026"))).containsExactly("dd-MMM-yyyy");
    assertThat(DateFormatDetector.candidates(List.of("2026-07-09 14:32:10"))).containsExactly("yyyy-MM-dd HH:mm:ss");
    assertThat(DateFormatDetector.candidates(List.of("20260709"))).containsExactly("yyyyMMdd");
  }

  @Test
  void oneStrayBadValueDoesNotStopDetection() {
    var samples = List.of("01/07/2026", "15/07/2026", "2026-07-01", "16/07/2026");

    assertThat(DateFormatDetector.candidates(samples)).containsExactly("dd/MM/yyyy");
    assertThat(DateFormatDetector.failures(samples, "dd/MM/yyyy")).isEqualTo(1);
  }

  @Test
  void theToleranceNeverMakesAClearSampleAmbiguous() {
    // MM/dd/yyyy fails only on 13/07, which the tolerance would allow, but dd/MM/yyyy fits all four.
    assertThat(DateFormatDetector.candidates(List.of("01/07/2026", "01/07/2026", "02/07/2026", "13/07/2026")))
        .containsExactly("dd/MM/yyyy");
  }

  @Test
  void twoSamplesMustBothFit() {
    assertThat(DateFormatDetector.candidates(List.of("15/07/2026", "2026-07-01"))).isEmpty();
  }

  @Test
  void impossibleDatesAndUnknownLayoutsMatchNothing() {
    assertThat(DateFormatDetector.candidates(List.of("31/02/2026"))).isEmpty();
    assertThat(DateFormatDetector.candidates(List.of("July 9th"))).isEmpty();
    assertThat(DateFormatDetector.candidates(List.of())).isEmpty();
  }

  @Test
  void onlyAllowListedPatternsAreUsed() {
    assertThat(DateFormats.isAllowed("dd/MM/yyyy")).isTrue();
    assertThat(DateFormats.isAllowed("EEEE ''hack''")).isFalse();
    assertThat(DateFormats.parse("09/07/2026", "dd/MM/yyyy")).contains(LocalDate.of(2026, 7, 9));
  }
}

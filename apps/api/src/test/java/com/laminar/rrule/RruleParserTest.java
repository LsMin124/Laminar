package com.laminar.rrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.rrule.domain.Rrule;
import com.laminar.rrule.domain.RruleFrequency;
import com.laminar.rrule.domain.RruleParser;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RruleParser + Rrule.expand 검증 (unit, DB 무관). */
class RruleParserTest {

  @Test
  void parses_daily_with_count() {
    Rrule rule = RruleParser.parse("FREQ=DAILY;COUNT=3");

    assertThat(rule.freq()).isEqualTo(RruleFrequency.DAILY);
    assertThat(rule.interval()).isEqualTo(1);
    assertThat(rule.count()).isEqualTo(3);
    assertThat(rule.until()).isNull();
  }

  @Test
  void parses_weekly_with_interval_and_until() {
    Rrule rule = RruleParser.parse("FREQ=WEEKLY;INTERVAL=2;UNTIL=20260901");

    assertThat(rule.freq()).isEqualTo(RruleFrequency.WEEKLY);
    assertThat(rule.interval()).isEqualTo(2);
    assertThat(rule.until()).isEqualTo(LocalDate.of(2026, 9, 1));
  }

  @Test
  void rrule_prefix_optional() {
    Rrule rule = RruleParser.parse("RRULE:FREQ=MONTHLY;COUNT=2");

    assertThat(rule.freq()).isEqualTo(RruleFrequency.MONTHLY);
    assertThat(rule.count()).isEqualTo(2);
  }

  @Test
  void missing_freq_rejected() {
    assertThatThrownBy(() -> RruleParser.parse("INTERVAL=2"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FREQ");
  }

  @Test
  void unsupported_freq_rejected() {
    assertThatThrownBy(() -> RruleParser.parse("FREQ=YEARLY"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FREQ");
  }

  @Test
  void count_and_until_together_rejected() {
    assertThatThrownBy(() -> RruleParser.parse("FREQ=DAILY;COUNT=5;UNTIL=20260601"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("both COUNT and UNTIL");
  }

  @Test
  void daily_expand_count_3_returns_3_consecutive_days() {
    Rrule rule = RruleParser.parse("FREQ=DAILY;COUNT=3");

    List<LocalDate> dates =
        rule.expand(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

    assertThat(dates)
        .containsExactly(
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 3));
  }

  @Test
  void weekly_interval_2_skips_alternate_weeks() {
    Rrule rule = RruleParser.parse("FREQ=WEEKLY;INTERVAL=2;COUNT=3");

    List<LocalDate> dates =
        rule.expand(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31));

    assertThat(dates)
        .containsExactly(
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 29));
  }

  @Test
  void until_caps_expansion() {
    Rrule rule = RruleParser.parse("FREQ=DAILY;UNTIL=20260603");

    List<LocalDate> dates =
        rule.expand(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

    assertThat(dates).hasSize(3); // 6/1, 6/2, 6/3
  }

  @Test
  void window_excludes_before_start_and_after_end() {
    Rrule rule = RruleParser.parse("FREQ=DAILY;COUNT=10");

    List<LocalDate> dates =
        rule.expand(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 7));

    assertThat(dates)
        .containsExactly(
            LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 7));
  }
}

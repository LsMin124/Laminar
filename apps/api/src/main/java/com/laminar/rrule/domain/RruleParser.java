package com.laminar.rrule.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.Map;

/**
 * RFC 5545 RRULE 단순 파서 — FREQ + INTERVAL + COUNT + UNTIL 만 지원.
 *
 * <p>지원: - FREQ=DAILY / WEEKLY / MONTHLY - INTERVAL=정수 (기본 1) - COUNT=정수 (반복 횟수) - UNTIL=YYYYMMDD
 * or YYYYMMDDTHHMMSSZ (RFC 5545 date or datetime)
 *
 * <p>미지원: BYDAY, BYMONTHDAY, BYSETPOS, FREQ=HOURLY/MINUTELY/SECONDLY/YEARLY (후속).
 */
public final class RruleParser {

  private static final DateTimeFormatter UNTIL_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter UNTIL_DATETIME =
      new DateTimeFormatterBuilder()
          .parseCaseInsensitive()
          .appendValue(ChronoField.YEAR, 4)
          .appendValue(ChronoField.MONTH_OF_YEAR, 2)
          .appendValue(ChronoField.DAY_OF_MONTH, 2)
          .appendLiteral('T')
          .appendValue(ChronoField.HOUR_OF_DAY, 2)
          .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
          .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
          .appendLiteral('Z')
          .toFormatter();

  private RruleParser() {}

  public static Rrule parse(String rrule) {
    if (rrule == null || rrule.isBlank()) {
      throw new IllegalArgumentException("rrule blank");
    }
    String body = rrule.startsWith("RRULE:") ? rrule.substring("RRULE:".length()) : rrule;
    Map<String, String> parts = new HashMap<>();
    for (String token : body.split(";")) {
      String trimmed = token.trim();
      if (trimmed.isEmpty()) continue;
      int eq = trimmed.indexOf('=');
      if (eq < 0) {
        throw new IllegalArgumentException("invalid RRULE token: " + trimmed);
      }
      parts.put(trimmed.substring(0, eq).toUpperCase(), trimmed.substring(eq + 1));
    }

    String freqRaw = parts.get("FREQ");
    if (freqRaw == null) {
      throw new IllegalArgumentException("RRULE requires FREQ");
    }
    RruleFrequency freq = RruleFrequency.fromValue(freqRaw);

    int interval = 1;
    if (parts.containsKey("INTERVAL")) {
      interval = Integer.parseInt(parts.get("INTERVAL"));
      if (interval < 1) throw new IllegalArgumentException("INTERVAL must be >= 1");
    }

    Integer count = parts.containsKey("COUNT") ? Integer.parseInt(parts.get("COUNT")) : null;
    if (count != null && count < 1) {
      throw new IllegalArgumentException("COUNT must be >= 1");
    }

    LocalDate until = parts.containsKey("UNTIL") ? parseUntil(parts.get("UNTIL")) : null;

    if (count != null && until != null) {
      throw new IllegalArgumentException("RRULE cannot have both COUNT and UNTIL");
    }

    return new Rrule(freq, interval, count, until);
  }

  private static LocalDate parseUntil(String raw) {
    try {
      return raw.length() == 8
          ? LocalDate.parse(raw, UNTIL_DATE)
          : LocalDate.parse(raw, UNTIL_DATETIME);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "invalid UNTIL: " + raw + " (expected YYYYMMDD or YYYYMMDDTHHMMSSZ)");
    }
  }
}

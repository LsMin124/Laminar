package com.laminar.rrule;

import java.time.LocalDate;
import java.util.Arrays;

public enum RruleFrequency {
  DAILY {
    @Override
    public LocalDate advance(LocalDate cursor, int interval) {
      return cursor.plusDays(interval);
    }
  },
  WEEKLY {
    @Override
    public LocalDate advance(LocalDate cursor, int interval) {
      return cursor.plusWeeks(interval);
    }
  },
  MONTHLY {
    @Override
    public LocalDate advance(LocalDate cursor, int interval) {
      return cursor.plusMonths(interval);
    }
  };

  public abstract LocalDate advance(LocalDate cursor, int interval);

  public static RruleFrequency fromValue(String raw) {
    return Arrays.stream(values())
        .filter(f -> f.name().equalsIgnoreCase(raw))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unsupported FREQ: " + raw));
  }
}

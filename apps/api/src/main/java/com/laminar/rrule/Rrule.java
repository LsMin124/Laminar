package com.laminar.rrule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 파싱된 RRULE — FREQ + INTERVAL + (COUNT 또는 UNTIL).
 *
 * expand(dtstart, windowStart, windowEnd) — dtstart부터 starting하여 window 안의 occurrence date 반환.
 */
public record Rrule(
        RruleFrequency freq,
        int interval,
        Integer count,
        LocalDate until
) {

    /**
     * occurrence date 생성 — window 안의 인스턴스만 반환.
     */
    public List<LocalDate> expand(LocalDate dtstart, LocalDate windowStart, LocalDate windowEnd) {
        if (dtstart == null) throw new IllegalArgumentException("dtstart required");
        if (windowStart == null || windowEnd == null) {
            throw new IllegalArgumentException("window required");
        }
        if (windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException("windowEnd must be >= windowStart");
        }

        List<LocalDate> result = new ArrayList<>();
        LocalDate cursor = dtstart;
        int generated = 0;
        int maxIterations = 10_000;

        for (int i = 0; i < maxIterations; i++) {
            if (count != null && generated >= count) break;
            if (until != null && cursor.isAfter(until)) break;
            if (cursor.isAfter(windowEnd)) break;

            if (!cursor.isBefore(windowStart)) {
                result.add(cursor);
            }
            generated++;
            cursor = freq.advance(cursor, interval);
        }
        return result;
    }
}

/**
 * 캘린더 month/week 그리드 계산 유틸.
 * - month: 해당 달이 속한 주 단위로 시작 (일요일~토요일 기준), 6주 grid (42 cells)
 * - week: 단일 주 7 cells
 *
 * 멀티데이 카드 overlap 레이아웃: 같은 주 안에서 시작일~종료일을 lane 단위로 쌓아 올림.
 */

import {
  addDays,
  differenceInCalendarDays,
  endOfMonth,
  endOfWeek,
  format,
  isSameDay,
  isWithinInterval,
  startOfMonth,
  startOfWeek,
} from "date-fns";
import type { CardResponse } from "./types";

export interface CalendarCell {
  date: Date;
  iso: string;
  inMonth: boolean;
}

export interface MonthGrid {
  monthStart: Date;
  monthEnd: Date;
  gridStart: Date;
  gridEnd: Date;
  weeks: CalendarCell[][];
}

export function buildMonthGrid(anchor: Date): MonthGrid {
  const monthStart = startOfMonth(anchor);
  const monthEnd = endOfMonth(anchor);
  const gridStart = startOfWeek(monthStart, { weekStartsOn: 0 });
  const gridEnd = endOfWeek(monthEnd, { weekStartsOn: 0 });

  const weeks: CalendarCell[][] = [];
  let cursor = gridStart;
  while (cursor <= gridEnd) {
    const week: CalendarCell[] = [];
    for (let i = 0; i < 7; i += 1) {
      week.push({
        date: cursor,
        iso: format(cursor, "yyyy-MM-dd"),
        inMonth:
          cursor.getMonth() === monthStart.getMonth() &&
          cursor.getFullYear() === monthStart.getFullYear(),
      });
      cursor = addDays(cursor, 1);
    }
    weeks.push(week);
  }
  return { monthStart, monthEnd, gridStart, gridEnd, weeks };
}

export interface CardLayoutSegment {
  card: CardResponse;
  weekIndex: number;
  startCol: number;
  endCol: number;
  lane: number;
  continuesLeft: boolean;
  continuesRight: boolean;
}

export function layoutCardsOnMonth(
  grid: MonthGrid,
  cards: CardResponse[],
): { segments: CardLayoutSegment[]; maxLanesPerWeek: number[] } {
  const segments: CardLayoutSegment[] = [];
  const maxLanesPerWeek = grid.weeks.map(() => 0);
  const laneOccupancy: Array<Array<Set<number>>> = grid.weeks.map(() => []);

  const sorted = [...cards]
    .filter((c) => c.startDate)
    .sort((a, b) => {
      const aStart = a.startDate!;
      const bStart = b.startDate!;
      if (aStart !== bStart) return aStart < bStart ? -1 : 1;
      const aSpan = spanDays(a);
      const bSpan = spanDays(b);
      return bSpan - aSpan;
    });

  for (const card of sorted) {
    const start = parseIsoDate(card.startDate!);
    const end = card.endDate ? parseIsoDate(card.endDate) : start;
    if (end < grid.gridStart || start > grid.gridEnd) continue;

    grid.weeks.forEach((week, weekIndex) => {
      const weekStart = week[0].date;
      const weekEnd = week[6].date;
      if (end < weekStart || start > weekEnd) return;

      const segStart = start < weekStart ? weekStart : start;
      const segEnd = end > weekEnd ? weekEnd : end;
      const startCol = differenceInCalendarDays(segStart, weekStart);
      const endCol = differenceInCalendarDays(segEnd, weekStart);

      let lane = 0;
      while (true) {
        const occupied = laneOccupancy[weekIndex][lane] ?? new Set<number>();
        let conflict = false;
        for (let c = startCol; c <= endCol; c += 1) {
          if (occupied.has(c)) {
            conflict = true;
            break;
          }
        }
        if (!conflict) {
          for (let c = startCol; c <= endCol; c += 1) occupied.add(c);
          laneOccupancy[weekIndex][lane] = occupied;
          break;
        }
        lane += 1;
      }

      segments.push({
        card,
        weekIndex,
        startCol,
        endCol,
        lane,
        continuesLeft: start < weekStart,
        continuesRight: end > weekEnd,
      });
      maxLanesPerWeek[weekIndex] = Math.max(
        maxLanesPerWeek[weekIndex],
        lane + 1,
      );
    });
  }

  return { segments, maxLanesPerWeek };
}

function spanDays(c: CardResponse): number {
  if (!c.startDate) return 0;
  const start = parseIsoDate(c.startDate);
  const end = c.endDate ? parseIsoDate(c.endDate) : start;
  return Math.max(1, differenceInCalendarDays(end, start) + 1);
}

export function parseIsoDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function isToday(date: Date): boolean {
  return isSameDay(date, new Date());
}

export function isInMonth(date: Date, anchor: Date): boolean {
  return (
    date.getMonth() === anchor.getMonth() &&
    date.getFullYear() === anchor.getFullYear()
  );
}

export function isDateWithin(
  iso: string,
  range: { from: string; to: string },
): boolean {
  return isWithinInterval(parseIsoDate(iso), {
    start: parseIsoDate(range.from),
    end: parseIsoDate(range.to),
  });
}

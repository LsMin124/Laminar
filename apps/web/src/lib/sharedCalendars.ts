/**
 * 공용 캘린더 + 공지 데이터 훅 — lib/equipment.ts 리소스별 분리 (DX-2′).
 * 주제 공유 공지 게시판. 캘린더(여러 개 가능, 장비 연동 1:1 또는 일반) + 날짜 범위 공지.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { equipmentKeys } from "./equipmentKeys";
import type { Announcement, SharedCalendar } from "./equipmentTypes";

export function useSharedCalendars() {
  return useQuery({
    queryKey: equipmentKeys.sharedCalendars,
    queryFn: () => api.get<SharedCalendar[]>("/api/shared-calendars"),
  });
}

export function useCreateSharedCalendar() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      name: string;
      announcementOnly: boolean;
      equipmentId?: string | null;
      color?: string | null;
    }) =>
      api.post<SharedCalendar>("/api/shared-calendars", {
        name: input.name,
        announcementOnly: input.announcementOnly,
        equipmentId: input.equipmentId ?? null,
        color: input.color ?? null,
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: equipmentKeys.sharedCalendars }),
  });
}

export function useDeleteSharedCalendar() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (calendarId: string) => api.delete<void>(`/api/shared-calendars/${calendarId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: equipmentKeys.sharedCalendars }),
  });
}

export function useAnnouncements(calendarId: string | null, fromIso: string, toIso: string) {
  return useQuery({
    queryKey: equipmentKeys.announcements(calendarId, fromIso, toIso),
    queryFn: () =>
      api.get<Announcement[]>(
        `/api/shared-calendars/${calendarId}/announcements?from=${encodeURIComponent(
          fromIso,
        )}&to=${encodeURIComponent(toIso)}`,
      ),
    enabled: !!calendarId,
  });
}

export function usePostAnnouncement(calendarId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      startAt: string;
      endAt?: string | null;
      title: string;
      bodyMd?: string | null;
    }) =>
      api.post<Announcement>(`/api/shared-calendars/${calendarId}/announcements`, {
        startAt: input.startAt,
        endAt: input.endAt ?? null,
        title: input.title,
        bodyMd: input.bodyMd ?? null,
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: equipmentKeys.announcementsAll }),
  });
}

export function useDeleteAnnouncement() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (announcementId: string) =>
      api.delete<void>(`/api/announcements/${announcementId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: equipmentKeys.announcementsAll }),
  });
}

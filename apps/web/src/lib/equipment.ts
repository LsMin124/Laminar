/**
 * 장비(공용 자원) 데이터 레이어 — 백엔드 /api/equipment(subject-shared) CRUD.
 *
 * 장비는 주제(워크스페이스) 단위로 모든 멤버가 공유한다. 표준 X-Laminar-Subject-Id 헤더로 접근
 * (EquipmentService는 PERSONAL 스코프 + canWrite 허용). 삭제는 OWNER만(백엔드 강제 → 403).
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";

export interface Equipment {
  id: string;
  subjectId: string;
  createdBy: string | null;
  name: string;
  description: string | null;
  location: string | null;
  active: boolean;
  /** 로그 시트 컬럼 정의(JSONB) — 로그 기능 증분에서 사용, 현재는 빈 배열로 둠. */
  defaultLogColumns: Record<string, unknown>[];
  createdAt: string;
  updatedAt: string;
}

const EQUIPMENT_KEY = ["equipment"] as const;

/** 주제의 전체 장비(비활성 포함) — 활성 필터는 뷰에서 클라이언트 측 토글. */
export function useEquipment() {
  return useQuery({
    queryKey: EQUIPMENT_KEY,
    queryFn: () => api.get<Equipment[]>("/api/equipment?activeOnly=false"),
  });
}

export function useCreateEquipment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: string; description?: string | null; location?: string | null }) =>
      api.post<Equipment>("/api/equipment", {
        name: input.name,
        description: input.description ?? null,
        location: input.location ?? null,
        defaultLogColumns: [],
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: EQUIPMENT_KEY }),
  });
}

export function useUpdateEquipment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      id: string;
      name?: string;
      description?: string | null;
      location?: string | null;
    }) => {
      const { id, ...patch } = input;
      return api.patch<Equipment>(`/api/equipment/${id}`, patch);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: EQUIPMENT_KEY }),
  });
}

export function useToggleEquipmentActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { id: string; active: boolean }) =>
      api.post<Equipment>(`/api/equipment/${input.id}/toggle-active`, { active: input.active }),
    onSuccess: () => qc.invalidateQueries({ queryKey: EQUIPMENT_KEY }),
  });
}

export function useDeleteEquipment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/equipment/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: EQUIPMENT_KEY }),
  });
}

// ── 장비 예약 ──────────────────────────────────────────────────────────
// 예약은 subject-shared·시간 겹침 차단(409). 시각은 ISO OffsetDateTime 문자열.

export interface Reservation {
  id: string;
  subjectId: string;
  equipmentId: string;
  reservedBy: string;
  startAt: string;
  endAt: string;
  purpose: string | null;
  rrule: string | null;
  cardId: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 한 장비의 [from,to) 범위 예약 — 백엔드가 from/to(ISO) 쿼리를 필수로 요구한다. */
export function useReservations(equipmentId: string | null, fromIso: string, toIso: string) {
  return useQuery({
    queryKey: ["reservations", equipmentId, fromIso, toIso],
    queryFn: () =>
      api.get<Reservation[]>(
        `/api/equipment/${equipmentId}/reservations?from=${encodeURIComponent(
          fromIso,
        )}&to=${encodeURIComponent(toIso)}`,
      ),
    enabled: !!equipmentId,
  });
}

/** 인증 사용자의 전체 예약(모든 장비, startAt DESC). */
export function useMyReservations() {
  return useQuery({
    queryKey: ["my-reservations"],
    queryFn: () => api.get<Reservation[]>("/api/me/reservations"),
  });
}

function invalidateReservations(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ["reservations"] });
  qc.invalidateQueries({ queryKey: ["my-reservations"] });
}

export function useCreateReservation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      equipmentId: string;
      startAt: string;
      endAt: string;
      purpose?: string | null;
    }) =>
      api.post<Reservation>(`/api/equipment/${input.equipmentId}/reservations`, {
        startAt: input.startAt,
        endAt: input.endAt,
        purpose: input.purpose ?? null,
      }),
    onSuccess: () => invalidateReservations(qc),
  });
}

export function useCancelReservation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (reservationId: string) => api.delete<void>(`/api/reservations/${reservationId}`),
    onSuccess: () => invalidateReservations(qc),
  });
}

// ── 장비 로그 시트(동적 컬럼) ──────────────────────────────────────────────
// 컬럼은 장비별 정의(type=TEXT/NUMBER/ENUM/BOOL/DATETIME), 로그 행은 columnKey→값(JSONB).

export type LogColumnType = "TEXT" | "NUMBER" | "ENUM" | "BOOL" | "DATETIME";

export interface LogColumn {
  id: string;
  equipmentId: string;
  columnKey: string;
  columnLabel: string;
  columnType: LogColumnType;
  enumValues: string[] | null;
  required: boolean;
  priority: number;
  defaultValue: string | null;
}

export interface LogEntry {
  id: string;
  equipmentId: string;
  loggedBy: string;
  loggedAt: string;
  reservationId: string | null;
  values: Record<string, unknown>;
  notes: string | null;
  createdAt: string;
}

export function useLogColumns(equipmentId: string | null) {
  return useQuery({
    queryKey: ["log-columns", equipmentId],
    queryFn: () => api.get<LogColumn[]>(`/api/equipment/${equipmentId}/log-columns`),
    enabled: !!equipmentId,
  });
}

export function useCreateLogColumn(equipmentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      columnKey: string;
      columnLabel: string;
      columnType: LogColumnType;
      enumValues?: string[] | null;
      required: boolean;
      defaultValue?: string | null;
    }) => api.post<LogColumn>(`/api/equipment/${equipmentId}/log-columns`, input),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["log-columns", equipmentId] }),
  });
}

export function useDeleteLogColumn(equipmentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (columnId: string) => api.delete<void>(`/api/log-columns/${columnId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["log-columns", equipmentId] }),
  });
}

export function useLogs(equipmentId: string | null) {
  return useQuery({
    queryKey: ["logs", equipmentId],
    queryFn: () => api.get<LogEntry[]>(`/api/equipment/${equipmentId}/logs`),
    enabled: !!equipmentId,
  });
}

export function useCreateLog(equipmentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      loggedAt?: string | null;
      values: Record<string, string>;
      notes?: string | null;
    }) => api.post<LogEntry>(`/api/equipment/${equipmentId}/logs`, input),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["logs", equipmentId] }),
  });
}

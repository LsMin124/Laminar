/**
 * 장비(공용 자원) CRUD 데이터 훅 — 백엔드 /api/equipment(subject-shared).
 *
 * 장비는 주제(워크스페이스) 단위로 모든 멤버가 공유한다. 표준 X-Laminar-Subject-Id 헤더로 접근
 * (EquipmentService는 PERSONAL 스코프 + canWrite 허용). 삭제는 OWNER만(백엔드 강제 → 403).
 *
 * DX-2′: 예약(reservations)·로그(equipmentLogs)·공용캘린더(sharedCalendars)는 리소스별 모듈로
 * 분리 — 타입은 equipmentTypes, 키는 equipmentKeys가 정본.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { equipmentKeys } from "./equipmentKeys";
import type { Equipment } from "./equipmentTypes";

/** 주제의 전체 장비(비활성 포함) — 활성 필터는 뷰에서 클라이언트 측 토글. */
export function useEquipment() {
  return useQuery({
    queryKey: equipmentKeys.equipment,
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
    onSuccess: () => qc.invalidateQueries({ queryKey: equipmentKeys.equipment }),
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
    onSuccess: () => qc.invalidateQueries({ queryKey: equipmentKeys.equipment }),
  });
}

export function useToggleEquipmentActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { id: string; active: boolean }) =>
      api.post<Equipment>(`/api/equipment/${input.id}/toggle-active`, { active: input.active }),
    onSuccess: () => qc.invalidateQueries({ queryKey: equipmentKeys.equipment }),
  });
}

export function useDeleteEquipment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/equipment/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: equipmentKeys.equipment }),
  });
}

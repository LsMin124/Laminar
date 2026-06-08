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

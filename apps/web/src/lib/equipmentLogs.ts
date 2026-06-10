/**
 * 장비 로그 시트(동적 컬럼) 데이터 훅 — lib/equipment.ts 리소스별 분리 (DX-2′).
 * 컬럼은 장비별 정의(type=TEXT/NUMBER/ENUM/BOOL/DATETIME), 로그 행은 columnKey→값(JSONB).
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { equipmentKeys } from "./equipmentKeys";
import type { LogColumn, LogColumnType, LogEntry } from "./equipmentTypes";

export function useLogColumns(equipmentId: string | null) {
  return useQuery({
    queryKey: equipmentKeys.logColumns(equipmentId),
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
    onSuccess: () => qc.invalidateQueries({ queryKey: equipmentKeys.logColumns(equipmentId) }),
  });
}

export function useDeleteLogColumn(equipmentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (columnId: string) => api.delete<void>(`/api/log-columns/${columnId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: equipmentKeys.logColumns(equipmentId) }),
  });
}

export function useLogs(equipmentId: string | null) {
  return useQuery({
    queryKey: equipmentKeys.logs(equipmentId),
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
    onSuccess: () => qc.invalidateQueries({ queryKey: equipmentKeys.logs(equipmentId) }),
  });
}

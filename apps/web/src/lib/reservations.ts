/** 장비 예약 데이터 훅 — lib/equipment.ts 리소스별 분리 (DX-2′). */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { equipmentKeys, invalidateReservations } from "./equipmentKeys";
import type { Reservation } from "./equipmentTypes";

/** 한 장비의 [from,to) 범위 예약 — 백엔드가 from/to(ISO) 쿼리를 필수로 요구한다. */
export function useReservations(equipmentId: string | null, fromIso: string, toIso: string) {
  return useQuery({
    queryKey: equipmentKeys.reservations(equipmentId, fromIso, toIso),
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
    queryKey: equipmentKeys.myReservations,
    queryFn: () => api.get<Reservation[]>("/api/me/reservations"),
  });
}

export function useCreateReservation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      equipmentId: string;
      startAt: string;
      endAt: string;
      purpose?: string | null;
      cardId?: string | null;
    }) =>
      api.post<Reservation>(`/api/equipment/${input.equipmentId}/reservations`, {
        startAt: input.startAt,
        endAt: input.endAt,
        purpose: input.purpose ?? null,
        cardId: input.cardId ?? null,
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

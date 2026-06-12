/**
 * LAB 훅 — 승격·멤버/역할·초대코드·가입 신청 (LAB재설계 L4).
 *
 * 관리 표면(/api/subjects/current/lab/**, /members)은 활성 주제 헤더 기반 — 호출 전 해당 lab으로
 * 전환돼 있어야 한다(SubjectLayout이 보장). UI의 역할별 숨김은 편의일 뿐 최종 강제는 서버 가드(§1.3).
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { useMe } from "./auth";
import { dagKeys } from "./dagKeys";

export type LabRole = "OWNER" | "ADMIN" | "MEMBER";

export interface LabMember {
  subjectId: string;
  userId: string;
  email: string | null;
  displayName: string | null;
  role: LabRole;
  joinedAt: string;
}

export interface LabInviteCode {
  code: string | null;
  createdAt: string | null;
}

export interface LabJoinRequest {
  id: string;
  userId: string;
  email: string | null;
  displayName: string | null;
  requestedAt: string;
}

export interface LabJoinOutcome {
  labId: string;
  labName: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
}

/** 키에 subjectId를 넣어 lab 전환 시 캐시가 섞이지 않게 한다. (모듈 내부 전용 — 외부는 훅으로만) */
const labKeys = {
  members: (subjectId: string) => ["lab", subjectId, "members"] as const,
  inviteCode: (subjectId: string) => ["lab", subjectId, "inviteCode"] as const,
  joinRequests: (subjectId: string) => ["lab", subjectId, "joinRequests"] as const,
};

export function useLabMembers(subjectId: string, enabled: boolean) {
  return useQuery({
    queryKey: labKeys.members(subjectId),
    queryFn: () => api.get<LabMember[]>("/api/subjects/current/members"),
    enabled,
  });
}

/**
 * 활성 lab에서 나의 역할 — 멤버 목록(전원 조회 가능) × me()로 도출. EquipmentView·LabPanel 등
 * §1.3 역할별 UI 게이팅의 공용 정본(LAB-P). 멤버 목록 도착 전·비멤버는 role=null(버튼 숨김 쪽 fail-closed).
 */
export function useMyLabRole(subjectId: string, enabled = true) {
  const me = useMe();
  const members = useLabMembers(subjectId, enabled);
  const role: LabRole | null =
    members.data?.find((m) => m.userId === me.data?.userId)?.role ?? null;
  return {
    role,
    isOwner: role === "OWNER",
    isAdmin: role === "OWNER" || role === "ADMIN",
    meId: me.data?.userId ?? null,
  };
}

/** 역할 변경 — OWNER 전용(서버 가드). */
export function useUpdateMemberRole(subjectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: LabRole }) =>
      api.patch<LabMember>(`/api/subjects/current/members/${userId}/role`, { role }),
    onSuccess: () => qc.invalidateQueries({ queryKey: labKeys.members(subjectId) }),
  });
}

/** 멤버 제거 — ADMIN+(ADMIN은 MEMBER만, 서버 가드). */
export function useRemoveMember(subjectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => api.delete<void>(`/api/subjects/current/members/${userId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: labKeys.members(subjectId) }),
  });
}

/** 현재 주제를 LAB으로 승격 — OWNER 전용, 강등 미지원(확인 다이얼로그는 호출부 책임). */
export function usePromoteToLab() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<unknown>("/api/subjects/current/promote-to-lab"),
    onSuccess: () => qc.invalidateQueries({ queryKey: dagKeys.subjects }),
  });
}

export function useLabInviteCode(subjectId: string, enabled: boolean) {
  return useQuery({
    queryKey: labKeys.inviteCode(subjectId),
    queryFn: () => api.get<LabInviteCode>("/api/subjects/current/lab/invite-code"),
    enabled,
  });
}

/** 초대코드 발급/회전 — 기존 코드는 즉시 무효(ADMIN+). */
export function useRotateInviteCode(subjectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<LabInviteCode>("/api/subjects/current/lab/invite-code"),
    onSuccess: (fresh) => qc.setQueryData(labKeys.inviteCode(subjectId), fresh),
  });
}

export function useLabJoinRequests(subjectId: string, enabled: boolean) {
  return useQuery({
    queryKey: labKeys.joinRequests(subjectId),
    queryFn: () => api.get<LabJoinRequest[]>("/api/subjects/current/lab/join-requests"),
    enabled,
  });
}

/** 가입 신청 승인/거절 — 승인 시 MEMBER로 합류하므로 멤버 목록도 무효화. */
export function useDecideJoinRequest(subjectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, approve }: { requestId: string; approve: boolean }) =>
      api.post<void>(
        `/api/subjects/current/lab/join-requests/${requestId}/${approve ? "approve" : "reject"}`,
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: labKeys.joinRequests(subjectId) });
      void qc.invalidateQueries({ queryKey: labKeys.members(subjectId) });
    },
  });
}

/**
 * 초대코드로 LAB 가입 신청 — 승인 대기(pending) 생성. 신청자는 그 lab의 비멤버라 subject 헤더가 붙으면
 * 서버 컨텍스트 필터가 코드 조회를 가로막는다(LabJoinController 주석) — skipSubjectHeader로 SYSTEM 진입.
 */
export function useJoinLab() {
  return useMutation({
    mutationFn: (code: string) =>
      api.post<LabJoinOutcome>("/api/labs/join", { code }, { skipSubjectHeader: true }),
  });
}

/** 주제(Subject) 데이터 훅 — lib/dag.ts 리소스별 분리 (DX-2). */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { dagKeys } from "./dagKeys";
import type { Subject } from "./graphTypes";
import { optimisticUpdate, rollbackTo } from "./optimistic";
import { slugify } from "./slug";

export function useSubjects() {
  return useQuery({
    queryKey: dagKeys.subjects,
    // 헤더 생략 → SYSTEM scope로 내 전체 주제 조회(헤더가 있으면 subjectSharedFilter가 활성 주제 1개로 제한).
    queryFn: () => api.get<Subject[]>("/api/subjects", { skipSubjectHeader: true }),
  });
}

export function useCreateSubject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) =>
      api.post<Subject>("/api/subjects", {
        name,
        slug: slugify(name),
        defaultTimezone: "Asia/Seoul",
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: dagKeys.subjects }),
  });
}

/**
 * 현재(활성) 주제 수정(이름·본문) — 백엔드 PATCH /current는 헤더의 활성 주제를 대상으로 한다.
 * `id`는 본문 자동저장 낙관적 반영(subjects 캐시 행 갱신)용으로만 쓰이고 요청 본문엔 보내지 않는다.
 */
export function useUpdateSubject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ name, bodyMd }: { id?: string; name?: string; bodyMd?: string | null }) =>
      api.patch<Subject>("/api/subjects/current", { name, bodyMd }),
    onMutate: ({ id, name, bodyMd }) =>
      optimisticUpdate<Subject[]>(qc, dagKeys.subjects, (list) =>
        id
          ? list.map((s) =>
              s.id === id
                ? {
                    ...s,
                    ...(name !== undefined ? { name } : {}),
                    ...(bodyMd !== undefined ? { bodyMd } : {}),
                  }
                : s,
            )
          : list,
      ),
    onError: rollbackTo<Subject[]>(qc, dagKeys.subjects),
    onSettled: () => qc.invalidateQueries({ queryKey: dagKeys.subjects }),
  });
}

/**
 * 현재(활성) 주제 삭제 — 자식(탭·카드·관계·그룹) 전부 영구 삭제(백엔드 FK CASCADE).
 * 헤더 초기화·활성 주제 재선정·캐시 무효화는 호출부(SubjectLayout)가 순서대로 처리한다.
 */
export function useDeleteSubject() {
  return useMutation({
    mutationFn: () => api.delete<void>("/api/subjects/current"),
  });
}

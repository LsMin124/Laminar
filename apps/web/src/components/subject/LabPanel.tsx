import type { Subject } from "../../lib/graphTypes";
import {
  type LabMember,
  type LabRole,
  useDecideJoinRequest,
  useLabInviteCode,
  useLabJoinRequests,
  useLabMembers,
  useMyLabRole,
  useRemoveMember,
  useRotateInviteCode,
  useUpdateMemberRole,
} from "../../lib/labs";
import { useDialogs } from "../ui/DialogProvider";

/**
 * LAB 관리 패널 — 멤버/역할(§1.3 매트릭스), 초대코드, 가입 신청 대기열 (LAB재설계 L4).
 *
 * 호출 전 SubjectLayout이 해당 lab으로 전환을 보장한다(/current 기반 API). 역할별 노출:
 * 멤버 목록=전원, 역할 변경=OWNER, 제거=ADMIN+(ADMIN은 MEMBER만), 초대코드·승인=ADMIN+.
 */
export function LabPanel({ subject, onClose }: { subject: Subject; onClose: () => void }) {
  const dialogs = useDialogs();
  const members = useLabMembers(subject.id, true);
  const { role: myRole, isOwner, isAdmin } = useMyLabRole(subject.id);

  const inviteCode = useLabInviteCode(subject.id, isAdmin);
  const rotateCode = useRotateInviteCode(subject.id);
  const joinRequests = useLabJoinRequests(subject.id, isAdmin);
  const decideJoin = useDecideJoinRequest(subject.id);
  const updateRole = useUpdateMemberRole(subject.id);
  const removeMember = useRemoveMember(subject.id);

  function canRemove(target: LabMember): boolean {
    if (target.userId === subject.ownerUserId) return false; // 원소유자 제거 불가(서버도 차단)
    if (isOwner) return true;
    return isAdmin && target.role === "MEMBER";
  }

  async function onRotate() {
    const ok = await dialogs.confirm({
      title: "초대코드 재발급",
      message: "기존 코드는 즉시 무효가 됩니다. 재발급할까요?",
      confirmLabel: "재발급",
    });
    if (!ok) return;
    await rotateCode.mutateAsync();
  }

  async function onRemove(target: LabMember) {
    const ok = await dialogs.confirm({
      title: "멤버 제거",
      message: `${target.displayName ?? target.email ?? "이 멤버"}를 LAB에서 제거할까요?`,
      confirmLabel: "제거",
      danger: true,
    });
    if (!ok) return;
    await removeMember.mutateAsync(target.userId);
  }

  async function copyCode(code: string) {
    try {
      await navigator.clipboard.writeText(code);
    } catch {
      await dialogs.alert({ title: "복사 실패", message: `코드를 직접 복사하세요: ${code}` });
    }
  }

  return (
    <div className="subj-overlay" onClick={onClose}>
      <div
        className="subj-modal lab-panel"
        role="dialog"
        aria-label="LAB 관리"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="subj-head">
          <strong>
            LAB 관리 — {subject.name} {myRole && <span className="lab-role-chip">{myRole}</span>}
          </strong>
          <button type="button" className="subj-x" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </header>

        <section className="lab-section">
          <h4>멤버 ({members.data?.length ?? 0})</h4>
          <ul className="subj-list">
            {(members.data ?? []).map((m) => (
              <li key={m.userId} className="subj-row">
                <span className="lab-member-name">
                  {m.displayName ?? m.email ?? m.userId.slice(0, 8)}
                  {m.userId === subject.ownerUserId && (
                    <span className="lab-role-chip">원소유자</span>
                  )}
                </span>
                {isOwner && m.userId !== subject.ownerUserId ? (
                  <select
                    className="lab-role-select"
                    value={m.role}
                    onChange={(e) =>
                      updateRole.mutate(
                        { userId: m.userId, role: e.target.value as LabRole },
                        {
                          onError: () =>
                            void dialogs.alert({
                              title: "역할 변경 실패",
                              message: "역할을 변경하지 못했습니다. 잠시 후 다시 시도하세요.",
                            }),
                        },
                      )
                    }
                    aria-label="역할 변경"
                  >
                    <option value="ADMIN">ADMIN</option>
                    <option value="MEMBER">MEMBER</option>
                    {m.role === "OWNER" && <option value="OWNER">OWNER</option>}
                  </select>
                ) : (
                  <span className="lab-role-chip">{m.role}</span>
                )}
                {canRemove(m) && (
                  <button type="button" className="subj-act danger" onClick={() => void onRemove(m)}>
                    제거
                  </button>
                )}
              </li>
            ))}
            {members.isLoading && <li className="subj-empty">불러오는 중...</li>}
          </ul>
        </section>

        {isAdmin && (
          <section className="lab-section">
            <h4>초대코드</h4>
            <div className="lab-code-row">
              <code className="lab-code">{inviteCode.data?.code ?? "미발급"}</code>
              {inviteCode.data?.code && (
                <button
                  type="button"
                  className="subj-act"
                  onClick={() => void copyCode(inviteCode.data?.code ?? "")}
                >
                  복사
                </button>
              )}
              <button type="button" className="subj-act" onClick={() => void onRotate()}>
                {inviteCode.data?.code ? "재발급" : "발급"}
              </button>
            </div>
            <p className="lab-hint">코드를 받은 사용자가 가입 신청하면 아래 대기열에서 승인합니다.</p>
          </section>
        )}

        {isAdmin && (
          <section className="lab-section">
            <h4>가입 신청 ({joinRequests.data?.length ?? 0})</h4>
            <ul className="subj-list">
              {(joinRequests.data ?? []).map((r) => (
                <li key={r.id} className="subj-row">
                  <span className="lab-member-name">{r.displayName ?? r.email ?? r.userId}</span>
                  <button
                    type="button"
                    className="subj-act"
                    onClick={() => decideJoin.mutate({ requestId: r.id, approve: true })}
                  >
                    승인
                  </button>
                  <button
                    type="button"
                    className="subj-act danger"
                    onClick={() => decideJoin.mutate({ requestId: r.id, approve: false })}
                  >
                    거절
                  </button>
                </li>
              ))}
              {(joinRequests.data ?? []).length === 0 && (
                <li className="subj-empty">대기 중인 신청 없음</li>
              )}
            </ul>
          </section>
        )}
      </div>
    </div>
  );
}

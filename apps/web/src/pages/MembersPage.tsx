import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { api, ApiError } from "../lib/api";
import {
  useCurrentWorkspace,
  useMembers,
  usePendingInvitations,
  useRevokeInvitation,
  useUpdateMemberRole,
  useRemoveMember,
} from "../lib/queries";
import type { WorkspaceRole } from "../lib/types";
import "./MembersPage.css";

const ROLE_OPTIONS: WorkspaceRole[] = ["OWNER", "MEMBER", "VIEWER"];

export function MembersPage() {
  const navigate = useNavigate();
  const workspace = useCurrentWorkspace(true);
  const members = useMembers();
  const pending = usePendingInvitations();
  const revoke = useRevokeInvitation();
  const updateRole = useUpdateMemberRole();
  const removeMember = useRemoveMember();

  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<WorkspaceRole>("MEMBER");
  const [inviteToken, setInviteToken] = useState<string | null>(null);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [inviting, setInviting] = useState(false);

  async function handleInvite(event: FormEvent) {
    event.preventDefault();
    setInviteError(null);
    setInviteToken(null);
    setInviting(true);
    try {
      const res = await api.post<{
        invitationId: string;
        rawToken: string;
        email: string;
        role: string;
      }>("/api/workspaces/current/invitations", {
        email: inviteEmail,
        role: inviteRole,
      });
      setInviteToken(res.rawToken);
      setInviteEmail("");
      pending.refetch();
    } catch (e) {
      if (e instanceof ApiError) {
        setInviteError(`초대 실패: HTTP ${e.status}`);
      } else {
        setInviteError("초대 발송 중 오류");
      }
    } finally {
      setInviting(false);
    }
  }

  return (
    <div className="members-page">
      <header className="members-page-header">
        <button
          type="button"
          className="board-detail-back"
          onClick={() => navigate("/")}
        >
          ← 보드 목록
        </button>
        <h1>멤버</h1>
        {workspace.data && (
          <span className="members-workspace">/{workspace.data.slug}</span>
        )}
      </header>

      <section className="members-section">
        <h2>활성 멤버 ({members.data?.length ?? 0})</h2>
        {members.isLoading ? (
          <p className="loading">불러오는 중...</p>
        ) : (
          <ul className="members-list">
            {members.data?.map((m) => (
              <li key={m.userId} className="members-item">
                <div className="members-item-info">
                  <div className="members-item-name">
                    {m.displayName ?? "(이름 없음)"}
                  </div>
                  <div className="members-item-email">{m.email}</div>
                </div>
                <select
                  className="members-item-role"
                  value={m.role}
                  onChange={(e) =>
                    updateRole.mutate({
                      userId: m.userId,
                      role: e.target.value as WorkspaceRole,
                    })
                  }
                  disabled={updateRole.isPending}
                >
                  {ROLE_OPTIONS.map((r) => (
                    <option key={r} value={r}>
                      {r}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="members-item-remove"
                  onClick={() => {
                    if (confirm(`${m.email} 멤버를 제거할까요?`)) {
                      removeMember.mutate(m.userId);
                    }
                  }}
                  disabled={removeMember.isPending}
                >
                  제거
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="members-section">
        <h2>새 초대</h2>
        <form className="invite-form" onSubmit={handleInvite}>
          <input
            type="email"
            placeholder="email@example.com"
            value={inviteEmail}
            onChange={(e) => setInviteEmail(e.target.value)}
            required
          />
          <select
            value={inviteRole}
            onChange={(e) => setInviteRole(e.target.value as WorkspaceRole)}
          >
            {ROLE_OPTIONS.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
          <button type="submit" disabled={inviting} className="primary">
            {inviting ? "발송 중..." : "초대"}
          </button>
        </form>
        {inviteError && <p className="auth-error">{inviteError}</p>}
        {inviteToken && (
          <div className="invite-token">
            <p>초대 토큰 (수신자에게 전달, 7일 유효):</p>
            <code>{inviteToken}</code>
          </div>
        )}
      </section>

      <section className="members-section">
        <h2>대기 중 초대 ({pending.data?.length ?? 0})</h2>
        {pending.data && pending.data.length > 0 ? (
          <ul className="pending-list">
            {pending.data.map((inv) => (
              <li key={inv.id} className="pending-item">
                <div>
                  <strong>{inv.email}</strong>
                  <span className="pending-role">{inv.role}</span>
                </div>
                <span className="pending-expires">
                  만료 {new Date(inv.expiresAt).toLocaleDateString()}
                </span>
                <button
                  type="button"
                  className="members-item-remove"
                  onClick={() => revoke.mutate(inv.id)}
                  disabled={revoke.isPending}
                >
                  취소
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="markdown-empty">대기 중 초대 없음</p>
        )}
      </section>
    </div>
  );
}

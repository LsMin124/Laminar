import { useState, type FormEvent } from "react";
import { api } from "../lib/api";

interface Props {
  onBack: () => void;
}

/** 비밀번호 찾기 — 이메일 입력 → 재설정 링크 요청. 백엔드는 계정 존재와 무관하게 204(enumeration 차단). */
export function ForgotPasswordPage({ onBack }: Props) {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await api.post("/api/auth/password-reset/request", { email });
    } catch {
      // 실패해도 동일 안내(노출 최소화).
    }
    setBusy(false);
    setSent(true);
  }

  return (
    <div className="auth-container">
      <h1>비밀번호 찾기</h1>
      {sent ? (
        <>
          <p className="auth-switch">
            입력하신 이메일이 등록돼 있으면 재설정 링크를 보냈습니다. 메일함을 확인해 주세요. (링크는 1시간 동안 유효합니다.)
          </p>
          <p className="auth-switch">
            <button type="button" className="link" onClick={onBack}>
              로그인으로 돌아가기
            </button>
          </p>
        </>
      ) : (
        <>
          <form onSubmit={onSubmit} className="auth-form">
            <label>
              이메일
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
              />
            </label>
            <button type="submit" disabled={busy}>
              {busy ? "전송 중..." : "재설정 링크 보내기"}
            </button>
          </form>
          <p className="auth-switch">
            <button type="button" className="link" onClick={onBack}>
              로그인으로 돌아가기
            </button>
          </p>
        </>
      )}
    </div>
  );
}

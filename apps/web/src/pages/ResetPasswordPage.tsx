import { useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";

function tokenFromUrl(): string {
  return new URLSearchParams(window.location.search).get("token") ?? "";
}

/** 비밀번호 재설정 — 메일 링크(/reset?token=...)의 토큰 + 새 비밀번호 → confirm. 성공 시 로그인으로. */
export function ResetPasswordPage() {
  const [token] = useState(tokenFromUrl);
  const [password, setPassword] = useState("");
  const [confirmPw, setConfirmPw] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  const mismatch = confirmPw.length > 0 && password !== confirmPw;

  function goLogin() {
    window.location.href = "/";
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (password !== confirmPw) {
      setError("비밀번호가 일치하지 않습니다.");
      return;
    }
    setBusy(true);
    try {
      await api.post("/api/auth/password-reset/confirm", { token, password });
      setDone(true);
    } catch (err) {
      // 400 외(502·타임아웃 등)는 대개 머신 콜드스타트/재시작 중의 일시 오류 — 링크는 그대로 유효하다.
      setError(
        err instanceof ApiError && err.status === 400
          ? "링크가 만료되었거나 유효하지 않습니다. 다시 요청해 주세요."
          : "일시적인 서버 오류입니다. 몇 초 후 같은 링크에서 다시 시도해 주세요.",
      );
    }
    setBusy(false);
  }

  if (!token) {
    return (
      <div className="auth-container">
        <h1>비밀번호 재설정</h1>
        <p className="auth-error">유효하지 않은 링크입니다.</p>
        <p className="auth-switch">
          <button type="button" className="link" onClick={goLogin}>
            로그인으로
          </button>
        </p>
      </div>
    );
  }

  return (
    <div className="auth-container">
      <h1>비밀번호 재설정</h1>
      {done ? (
        <>
          <p className="auth-switch">비밀번호가 변경되었습니다. 새 비밀번호로 로그인하세요.</p>
          <button type="button" onClick={goLogin}>
            로그인하러 가기
          </button>
        </>
      ) : (
        <form onSubmit={onSubmit} className="auth-form">
          <label>
            새 비밀번호 (8자 이상)
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={8}
              maxLength={128}
              autoComplete="new-password"
            />
          </label>
          <label>
            비밀번호 확인
            <input
              type="password"
              value={confirmPw}
              onChange={(e) => setConfirmPw(e.target.value)}
              required
              minLength={8}
              maxLength={128}
              autoComplete="new-password"
              aria-invalid={mismatch}
            />
          </label>
          {mismatch && <p className="auth-error">비밀번호가 일치하지 않습니다.</p>}
          {error && <p className="auth-error">{error}</p>}
          <button type="submit" disabled={busy || mismatch || !confirmPw}>
            {busy ? "변경 중..." : "비밀번호 변경"}
          </button>
        </form>
      )}
    </div>
  );
}

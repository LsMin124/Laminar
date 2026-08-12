import { useEffect, useState, type FormEvent } from "react";
import { useLogin } from "../lib/auth";
import { ApiError } from "../lib/api";
import { apiEnvelopeMessage } from "../lib/apiErrors";
import { GoogleSignInButton } from "../components/auth/GoogleSignInButton";

/**
 * OAuth 콜백 실패 시 서버가 ?error 쿼리로 리다이렉트한다(Spring 기본 /login?error,
 * 성공 핸들러의 /?error=oauth_*). 무음으로 로그인 화면에 떨어지면 원인을 알 수 없으므로 여기서 표시한다.
 */
function oauthErrorMessage(): string | null {
  const err = new URLSearchParams(window.location.search).get("error");
  if (err === null) return null;
  if (err === "oauth_no_email") return "Google 계정에서 이메일을 확인할 수 없어 로그인하지 못했습니다.";
  if (err === "oauth_email_unverified") return "Google 이메일이 미인증 상태라 로그인할 수 없습니다.";
  return "Google 로그인에 실패했습니다. 다시 시도하거나 이메일·비밀번호로 로그인해 주세요.";
}

interface Props {
  onSwitchToSignup: () => void;
  onForgot: () => void;
}

export function LoginPage({ onSwitchToSignup, onForgot }: Props) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(oauthErrorMessage);
  const login = useLogin();

  // 오류 쿼리는 표시 후 URL에서 제거 — 새로고침·공유 시 재표시 방지.
  useEffect(() => {
    if (new URLSearchParams(window.location.search).has("error")) {
      window.history.replaceState(null, "", "/");
    }
  }, []);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await login.mutateAsync({ email, password });
    } catch (err) {
      if (err instanceof ApiError) {
        // err.message는 "POST /api/auth/login → 429" 같은 기술 문자열 — 노출하지 않는다.
        if (err.status === 400 || err.status === 401) {
          setError("이메일 또는 비밀번호가 올바르지 않습니다.");
        } else if (err.status === 429) {
          setError("로그인 시도가 너무 잦습니다. 잠시 후 다시 시도해 주세요.");
        } else {
          setError(apiEnvelopeMessage(err) ?? "로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
      } else {
        setError("로그인 중 오류가 발생했습니다.");
      }
    }
  }

  return (
    <div className="auth-container">
      <h1>Laminar 로그인</h1>
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
        <label>
          비밀번호
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
            minLength={8}
          />
        </label>
        {error && <p className="auth-error">{error}</p>}
        <button type="submit" disabled={login.isPending}>
          {login.isPending ? "로그인 중..." : "로그인"}
        </button>
      </form>
      <GoogleSignInButton label="Google로 로그인" />
      <p className="auth-switch">
        계정이 없으신가요?{" "}
        <button type="button" onClick={onSwitchToSignup} className="link">
          가입하기
        </button>
      </p>
      <p className="auth-switch">
        <button type="button" onClick={onForgot} className="link">
          비밀번호를 잊으셨나요?
        </button>
      </p>
    </div>
  );
}

import { useState, type FormEvent } from "react";
import { useSignup } from "../lib/auth";
import { ApiError } from "../lib/api";
import { apiEnvelopeMessage } from "../lib/apiErrors";
import { GoogleSignInButton } from "../components/auth/GoogleSignInButton";

interface Props {
  onSwitchToLogin: () => void;
}

export function SignupPage({ onSwitchToLogin }: Props) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const signup = useSignup();

  const passwordMismatch =
    confirmPassword.length > 0 && password !== confirmPassword;

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (password !== confirmPassword) {
      setError("비밀번호가 일치하지 않습니다. 다시 확인해주세요.");
      return;
    }
    try {
      await signup.mutateAsync({ email, password, displayName });
    } catch (err) {
      if (err instanceof ApiError) {
        // err.message는 "POST /api/auth/signup → 409" 같은 기술 문자열 — 사용자에겐 envelope의
        // 큐레이트 문구(409 "이미 가입된 이메일입니다." 등)를 보여준다.
        if (err.status === 400) {
          setError("입력값을 확인해주세요.");
        } else if (err.status === 429) {
          setError("가입 시도가 너무 잦습니다. 잠시 후 다시 시도해 주세요.");
        } else {
          setError(apiEnvelopeMessage(err) ?? "가입에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
      } else {
        setError("가입 중 오류가 발생했습니다.");
      }
    }
  }

  return (
    <div className="auth-container">
      <h1>Laminar 가입</h1>
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
          표시 이름
          <input
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
            maxLength={100}
            autoComplete="name"
          />
        </label>
        <label>
          비밀번호 (8자 이상)
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
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
            minLength={8}
            maxLength={128}
            autoComplete="new-password"
            aria-invalid={passwordMismatch}
          />
        </label>
        {passwordMismatch && (
          <p className="auth-error">비밀번호가 일치하지 않습니다.</p>
        )}
        {error && <p className="auth-error">{error}</p>}
        <button
          type="submit"
          disabled={signup.isPending || passwordMismatch || !confirmPassword}
        >
          {signup.isPending ? "가입 중..." : "가입하기"}
        </button>
      </form>
      <GoogleSignInButton label="Google로 가입" />
      <p className="auth-switch">
        이미 계정이 있으신가요?{" "}
        <button type="button" onClick={onSwitchToLogin} className="link">
          로그인
        </button>
      </p>
    </div>
  );
}

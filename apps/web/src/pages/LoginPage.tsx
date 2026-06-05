import { useState, type FormEvent } from "react";
import { useLogin } from "../lib/auth";
import { ApiError } from "../lib/api";
import { GoogleSignInButton } from "../components/auth/GoogleSignInButton";

interface Props {
  onSwitchToSignup: () => void;
}

export function LoginPage({ onSwitchToSignup }: Props) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const login = useLogin();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await login.mutateAsync({ email, password });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.status === 400 ? "이메일 또는 비밀번호가 올바르지 않습니다." : err.message);
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
    </div>
  );
}

/**
 * Google OAuth2 로그인/가입 진입 버튼.
 *
 * 백엔드 Spring Security oauth2Login의 권한요청 엔드포인트(/oauth2/authorization/google)로
 * 전체 페이지 리다이렉트한다. OAuth는 브라우저 리다이렉트 플로라 fetch가 아닌 <a> 내비게이션 사용.
 * 동일 출처(SPA가 Spring static에서 서빙)이므로 VITE_API_BASE가 비면 상대 경로.
 */
const GOOGLE_AUTH_URL = `${import.meta.env.VITE_API_BASE ?? ""}/oauth2/authorization/google`;

interface Props {
  label?: string;
}

export function GoogleSignInButton({ label = "Google로 계속하기" }: Props) {
  return (
    <div className="oauth-section">
      <div className="oauth-divider">
        <span>또는</span>
      </div>
      <a className="oauth-google-btn" href={GOOGLE_AUTH_URL}>
        <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
          <path
            fill="#4285F4"
            d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.71-1.57 2.68-3.89 2.68-6.62z"
          />
          <path
            fill="#34A853"
            d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.81.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18z"
          />
          <path
            fill="#FBBC05"
            d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33z"
          />
          <path
            fill="#EA4335"
            d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58z"
          />
        </svg>
        {label}
      </a>
    </div>
  );
}

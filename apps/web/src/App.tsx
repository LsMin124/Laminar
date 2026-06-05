import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useMe } from "./lib/auth";
import { LoginPage } from "./pages/LoginPage";
import { SignupPage } from "./pages/SignupPage";
import { DialogProvider } from "./components/ui/DialogProvider";
import { SubjectLayout } from "./components/dag/SubjectLayout";
import "./App.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false },
  },
});

/**
 * DAG 개편 — 단일 표면 셸: 인증 → 주제(Subject) 사이드바 + DAG 워크스페이스.
 * 주제 목록 부트스트랩·전환은 SubjectLayout이 담당.
 */
function Shell() {
  const me = useMe();
  const [authMode, setAuthMode] = useState<"login" | "signup">("login");

  if (me.isLoading) return <p className="loading">불러오는 중...</p>;

  if (!me.data) {
    return authMode === "login" ? (
      <LoginPage onSwitchToSignup={() => setAuthMode("signup")} />
    ) : (
      <SignupPage onSwitchToLogin={() => setAuthMode("login")} />
    );
  }

  return <SubjectLayout />;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <DialogProvider>
        <Shell />
      </DialogProvider>
    </QueryClientProvider>
  );
}

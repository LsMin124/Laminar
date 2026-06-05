import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useMe } from "./lib/auth";
import { setCurrentWorkspaceId, api } from "./lib/api";
import { LoginPage } from "./pages/LoginPage";
import { SignupPage } from "./pages/SignupPage";
import { DialogProvider } from "./components/ui/DialogProvider";
import { DagWorkspace } from "./components/dag/DagWorkspace";
import type { Subject } from "./lib/dag";
import "./App.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false },
  },
});

/**
 * DAG 개편 Phase 4 — 단일 표면 셸: 인증 → 주제(Subject) 부트스트랩 → DAG 워크스페이스.
 * 레거시 다중 페이지(보드 목록·장비·멤버·관리자·영구노트)는 라우팅에서 제외(파일은 잔존).
 */
function Shell() {
  const me = useMe();
  const [authMode, setAuthMode] = useState<"login" | "signup">("login");
  const [subjectReady, setSubjectReady] = useState(false);

  useEffect(() => {
    if (!me.data) return;
    const stored = localStorage.getItem("laminar.workspaceId");
    if (stored) {
      setCurrentWorkspaceId(stored);
      setSubjectReady(true);
      return;
    }
    (async () => {
      try {
        // 헤더 없는 SYSTEM scope에서 내 주제 목록 조회 → 첫 주제를 활성으로.
        const list = await api.get<Subject[]>("/api/subjects");
        if (list.length > 0) {
          setCurrentWorkspaceId(list[0].id);
        }
      } catch {
        // 무시 — 주제 미설정 상태로 진행.
      } finally {
        setSubjectReady(true);
      }
    })();
  }, [me.data]);

  if (me.isLoading) return <p className="loading">불러오는 중...</p>;

  if (!me.data) {
    return authMode === "login" ? (
      <LoginPage onSwitchToSignup={() => setAuthMode("signup")} />
    ) : (
      <SignupPage onSwitchToLogin={() => setAuthMode("login")} />
    );
  }

  if (!subjectReady) return <p className="loading">주제 확인 중...</p>;

  return <DagWorkspace />;
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

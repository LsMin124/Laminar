import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useMe } from "./lib/queries";
import { setCurrentWorkspaceId, api } from "./lib/api";
import { LoginPage } from "./pages/LoginPage";
import { SignupPage } from "./pages/SignupPage";
import { BoardsPage } from "./pages/BoardsPage";
import type { WorkspaceResponse } from "./lib/types";
import "./App.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false },
  },
});

function Shell() {
  const me = useMe();
  const [authMode, setAuthMode] = useState<"login" | "signup">("login");
  const [workspaceReady, setWorkspaceReady] = useState(false);

  useEffect(() => {
    if (!me.data) return;
    const stored = localStorage.getItem("laminar.workspaceId");
    if (stored) {
      setCurrentWorkspaceId(stored);
      setWorkspaceReady(true);
      return;
    }
    (async () => {
      try {
        const ws = await api.get<WorkspaceResponse>("/api/workspaces/current");
        setCurrentWorkspaceId(ws.id);
        setWorkspaceReady(true);
      } catch {
        setWorkspaceReady(true);
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

  if (!workspaceReady) return <p className="loading">워크스페이스 확인 중...</p>;

  return <BoardsPage />;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <Shell />
    </QueryClientProvider>
  );
}

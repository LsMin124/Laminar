import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route, Navigate } from "react-router";
import { useMe } from "./lib/queries";
import { setCurrentWorkspaceId, api } from "./lib/api";
import { LoginPage } from "./pages/LoginPage";
import { SignupPage } from "./pages/SignupPage";
import { BoardsPage } from "./pages/BoardsPage";
import { BoardDetailPage } from "./pages/BoardDetailPage";
import { CardDetailPage } from "./pages/CardDetailPage";
import { MembersPage } from "./pages/MembersPage";
import { PerpetualPage } from "./pages/PerpetualPage";
import { EquipmentPage } from "./pages/EquipmentPage";
import { EquipmentDetailPage } from "./pages/EquipmentDetailPage";
import { AdminPage } from "./pages/AdminPage";
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

  return (
    <Routes>
      <Route path="/" element={<BoardsPage />} />
      <Route path="/boards/:boardId" element={<BoardDetailPage />} />
      <Route
        path="/boards/:boardId/cards/:cardId"
        element={<CardDetailPage />}
      />
      <Route path="/members" element={<MembersPage />} />
      <Route path="/boards/:boardId/perpetual" element={<PerpetualPage />} />
      <Route path="/equipment" element={<EquipmentPage />} />
      <Route path="/equipment/:equipmentId" element={<EquipmentDetailPage />} />
      <Route path="/admin" element={<AdminPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Shell />
      </BrowserRouter>
    </QueryClientProvider>
  );
}

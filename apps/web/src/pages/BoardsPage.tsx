import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { useBoards, useCreateBoard, useCurrentWorkspace, useLogout } from "../lib/queries";
import { ApiError } from "../lib/api";

export function BoardsPage() {
  const navigate = useNavigate();
  const workspace = useCurrentWorkspace(true);
  const boards = useBoards(true);
  const createBoard = useCreateBoard();
  const logout = useLogout();
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await createBoard.mutateAsync({ name, slug });
      setName("");
      setSlug("");
      setShowCreate(false);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(`보드 생성 실패: ${err.status}`);
      } else {
        setError("보드 생성 중 오류");
      }
    }
  }

  return (
    <div className="boards-container">
      <header className="boards-header">
        <div>
          <h1>{workspace.data?.name ?? "워크스페이스"}</h1>
          <p className="workspace-slug">/{workspace.data?.slug}</p>
        </div>
        <div className="boards-header-actions">
          <button
            type="button"
            onClick={() => navigate("/equipment")}
            className="logout-btn"
          >
            공용 자원
          </button>
          <button
            type="button"
            onClick={() => navigate("/members")}
            className="logout-btn"
          >
            멤버
          </button>
          <button type="button" onClick={() => logout.mutate()} className="logout-btn">
            로그아웃
          </button>
        </div>
      </header>

      <section className="boards-section">
        <div className="boards-section-head">
          <h2>보드</h2>
          <button type="button" onClick={() => setShowCreate(!showCreate)}>
            {showCreate ? "취소" : "+ 새 보드"}
          </button>
        </div>

        {showCreate && (
          <form onSubmit={onCreate} className="board-create-form">
            <input
              type="text"
              placeholder="보드 이름"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={200}
            />
            <input
              type="text"
              placeholder="slug (영문 소문자, 하이픈)"
              value={slug}
              onChange={(e) => setSlug(e.target.value)}
              required
              maxLength={100}
              pattern="[a-z0-9\-]+"
            />
            <button type="submit" disabled={createBoard.isPending}>
              {createBoard.isPending ? "생성 중..." : "생성"}
            </button>
            {error && <p className="auth-error">{error}</p>}
          </form>
        )}

        {boards.isLoading && <p>보드 불러오는 중...</p>}
        {boards.isError && <p className="auth-error">보드 조회 실패</p>}
        {boards.data && boards.data.length === 0 && (
          <p className="boards-empty">아직 보드가 없습니다. 새 보드를 만들어보세요.</p>
        )}

        <ul className="board-list">
          {boards.data?.map((board) => (
            <li
              key={board.id}
              className="board-card"
              onClick={() => navigate(`/boards/${board.id}`)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === "Enter") navigate(`/boards/${board.id}`);
              }}
            >
              <div className="board-name">{board.name}</div>
              <div className="board-meta">/{board.slug} · {board.defaultView}</div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

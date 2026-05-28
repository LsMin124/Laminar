package com.laminar.board;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 보드 CRUD — PERSONAL scope 강제, Hibernate @Filter 자동 격리.
 *
 *   - create: priority = (현재 user의 max priority) + 100 (신규 보드는 항상 마지막에)
 *   - list: priority ASC, deleted_at IS NULL
 *   - update: 변경 필드만 (name·default_view·icon·settings)
 *   - softDelete: deleted_at = NOW (hard delete 절대 금지)
 *
 * 격리 정책:
 *   - 모든 메서드 진입 시 PERSONAL scope 강제 (canWrite 추가 검증은 mutation에서)
 *   - Hibernate @Filter가 SELECT WHERE workspace_id=? AND user_id=? 자동 추가
 *   - INSERT 시 workspace_id·user_id는 context에서 명시 set (필터는 SELECT 전용)
 */
@Service
public class BoardService {

    private static final int PRIORITY_STEP = 100;

    private final BoardRepository boardRepo;

    public BoardService(BoardRepository boardRepo) {
        this.boardRepo = boardRepo;
    }

    @Transactional
    public BoardEntity create(
            String name,
            String slug,
            BoardDefaultView defaultView,
            String iconName,
            String iconColor,
            Map<String, Object> settings) {
        WorkspaceContext ctx = requirePersonalWritable();

        int nextPriority = boardRepo.findFirstByDeletedAtIsNullOrderByPriorityDesc()
                .map(b -> b.getPriority() + PRIORITY_STEP)
                .orElse(PRIORITY_STEP);

        BoardEntity board = new BoardEntity();
        board.setWorkspaceId(ctx.workspaceId());
        board.setUserId(ctx.userId());
        board.setCreatedBy(ctx.userId());
        board.setName(name);
        board.setSlug(slug);
        board.setDefaultView(defaultView == null ? BoardDefaultView.CALENDAR : defaultView);
        board.setIconName(iconName);
        board.setIconColor(iconColor);
        board.setSettings(settings == null ? new HashMap<>() : settings);
        board.setPriority(nextPriority);
        return boardRepo.save(board);
    }

    @Transactional(readOnly = true)
    public List<BoardEntity> listActive() {
        WorkspaceContextHolder.requirePersonal();
        return boardRepo.findByDeletedAtIsNullOrderByPriorityAsc();
    }

    @Transactional(readOnly = true)
    public Optional<BoardEntity> findById(UUID boardId) {
        WorkspaceContext ctx = WorkspaceContextHolder.requirePersonal();
        return boardRepo.findById(boardId)
                .filter(b -> b.getDeletedAt() == null)
                .filter(b -> ctx.ownsPersonal(b.getWorkspaceId(), b.getUserId()));
    }

    @Transactional
    public BoardEntity update(
            UUID boardId,
            String name,
            BoardDefaultView defaultView,
            String iconName,
            String iconColor,
            Map<String, Object> settings) {
        WorkspaceContext ctx = requirePersonalWritable();
        BoardEntity board = boardRepo.findById(boardId)
                .filter(b -> b.getDeletedAt() == null)
                .filter(b -> ctx.ownsPersonal(b.getWorkspaceId(), b.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("board not found: " + boardId));

        if (name != null && !name.isBlank()) board.setName(name);
        if (defaultView != null) board.setDefaultView(defaultView);
        if (iconName != null) board.setIconName(iconName);
        if (iconColor != null) board.setIconColor(iconColor);
        if (settings != null) board.setSettings(settings);
        return boardRepo.save(board);
    }

    /**
     * DnD reorder — 클라이언트가 보낸 ID 순서대로 priority = (index+1) * 100 배치 UPDATE.
     * 누락된 보드는 priority 보존 (clientside가 전체 목록 전송 안 한 경우 안전).
     */
    @Transactional
    public List<BoardEntity> reorder(List<UUID> orderedBoardIds) {
        WorkspaceContext ctx = requirePersonalWritable();
        if (orderedBoardIds == null || orderedBoardIds.isEmpty()) {
            return List.of();
        }
        List<BoardEntity> result = new java.util.ArrayList<>(orderedBoardIds.size());
        for (int i = 0; i < orderedBoardIds.size(); i++) {
            UUID boardId = orderedBoardIds.get(i);
            int newPriority = (i + 1) * PRIORITY_STEP;
            boardRepo.findById(boardId)
                    .filter(b -> b.getDeletedAt() == null)
                    .filter(b -> ctx.ownsPersonal(b.getWorkspaceId(), b.getUserId()))
                    .ifPresent(b -> {
                        b.setPriority(newPriority);
                        result.add(boardRepo.save(b));
                    });
        }
        return result;
    }

    @Transactional
    public void softDelete(UUID boardId) {
        WorkspaceContext ctx = requirePersonalWritable();
        boardRepo.findById(boardId)
                .filter(b -> b.getDeletedAt() == null)
                .filter(b -> ctx.ownsPersonal(b.getWorkspaceId(), b.getUserId()))
                .ifPresent(board -> {
                    board.setDeletedAt(OffsetDateTime.now());
                    boardRepo.save(board);
                });
    }

    private WorkspaceContext requirePersonalWritable() {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
            throw new IllegalStateException("PERSONAL scope required");
        }
        if (!ctx.canWrite()) {
            throw new IllegalStateException("VIEWER cannot mutate boards");
        }
        return ctx;
    }
}

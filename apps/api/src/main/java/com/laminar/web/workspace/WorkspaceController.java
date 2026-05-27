package com.laminar.web.workspace;

import com.laminar.security.LaminarPrincipal;
import com.laminar.workspace.WorkspaceEntity;
import com.laminar.workspace.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/workspaces — 워크스페이스 CRUD.
 *
 * GET / POST는 워크스페이스 진입 전 (SYSTEM scope) 호출 가능.
 * /current 시리즈는 X-Laminar-Workspace-Id 헤더로 PERSONAL scope 진입 후 호출.
 */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public ResponseEntity<WorkspaceDtos.WorkspaceResponse> create(
            Authentication authentication,
            @Valid @RequestBody WorkspaceDtos.CreateRequest request) {
        LaminarPrincipal principal = requirePrincipal(authentication);
        WorkspaceEntity workspace = workspaceService.create(
                principal.userId(),
                request.name(),
                request.slug(),
                request.defaultTimezone());
        return ResponseEntity.ok(toResponse(workspace));
    }

    @GetMapping("/current")
    public ResponseEntity<WorkspaceDtos.WorkspaceResponse> current() {
        return ResponseEntity.ok(toResponse(workspaceService.requireCurrent()));
    }

    @PatchMapping("/current")
    public ResponseEntity<WorkspaceDtos.WorkspaceResponse> updateCurrent(
            @Valid @RequestBody WorkspaceDtos.UpdateRequest request) {
        WorkspaceEntity updated = workspaceService.updateCurrent(
                request.name(),
                request.defaultTimezone(),
                request.settings());
        return ResponseEntity.ok(toResponse(updated));
    }

    private LaminarPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LaminarPrincipal principal)) {
            throw new IllegalStateException("authentication required");
        }
        return principal;
    }

    private WorkspaceDtos.WorkspaceResponse toResponse(WorkspaceEntity ws) {
        return new WorkspaceDtos.WorkspaceResponse(
                ws.getId(),
                ws.getName(),
                ws.getSlug(),
                ws.getOwnerUserId(),
                ws.getDefaultTimezone(),
                ws.getSettings(),
                ws.getCreatedAt(),
                ws.getUpdatedAt());
    }
}

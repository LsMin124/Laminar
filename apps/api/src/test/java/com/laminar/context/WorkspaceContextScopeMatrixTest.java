package com.laminar.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laminar.workspace.WorkspaceRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 격리 매트릭스 5종 — WorkspaceContext API 레벨 (DB 통합은 Phase 4 인증 작업에 결합).
 *
 * <p>검증 매트릭스: 1. SYSTEM: system() 팩터리 → workspaceId·userId null, scope == SYSTEM 2.
 * WORKSPACE_SHARED: workspace(wsId) → userId null, scope == WORKSPACE_SHARED 3. PERSONAL/OWNER:
 * personal(wsId, uId, OWNER) → scope == PERSONAL, canWrite, isOwner 4. PERSONAL/VIEWER:
 * personal(wsId, uId, VIEWER) → scope == PERSONAL, !canWrite, !isOwner 5. invariant:
 * workspace(null) / personal(_, null) → IllegalArgumentException
 *
 * <p>Hibernate Filter 동작 (cross-user/cross-workspace SQL 누출 0건)은 Phase 4 통합 테스트에서 Testcontainers
 * PostgreSQL로 검증 — 본 unit은 scope 도출 규칙만 격리.
 */
class WorkspaceContextScopeMatrixTest {

  private static final UUID WORKSPACE_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID WORKSPACE_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-000000000012");

  @Test
  void matrix_1_system_scope() {
    WorkspaceContext ctx = WorkspaceContext.system();

    assertEquals(WorkspaceContext.Scope.SYSTEM, ctx.scope());
    assertNull(ctx.workspaceId());
    assertNull(ctx.userId());
    assertNull(ctx.userRole());
  }

  @Test
  void matrix_2_workspace_shared_scope() {
    WorkspaceContext ctx = WorkspaceContext.workspace(WORKSPACE_A);

    assertEquals(WorkspaceContext.Scope.WORKSPACE_SHARED, ctx.scope());
    assertEquals(WORKSPACE_A, ctx.workspaceId());
    assertNull(ctx.userId());
  }

  @Test
  void matrix_3_personal_owner_full_write() {
    WorkspaceContext ctx = WorkspaceContext.personal(WORKSPACE_A, USER_A, WorkspaceRole.OWNER);

    assertEquals(WorkspaceContext.Scope.PERSONAL, ctx.scope());
    assertEquals(WORKSPACE_A, ctx.workspaceId());
    assertEquals(USER_A, ctx.userId());
    assertTrue(ctx.isOwner());
    assertTrue(ctx.canWrite());
  }

  @Test
  void matrix_4_personal_viewer_read_only() {
    WorkspaceContext ctx = WorkspaceContext.personal(WORKSPACE_B, USER_B, WorkspaceRole.VIEWER);

    assertEquals(WorkspaceContext.Scope.PERSONAL, ctx.scope());
    assertFalse(ctx.isOwner());
    assertFalse(ctx.canWrite());
  }

  @Test
  void matrix_5_invariant_violations_throw() {
    assertThrows(IllegalArgumentException.class, () -> WorkspaceContext.workspace(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> WorkspaceContext.personal(null, USER_A, WorkspaceRole.MEMBER));
    assertThrows(
        IllegalArgumentException.class,
        () -> WorkspaceContext.personal(WORKSPACE_A, null, WorkspaceRole.MEMBER));
  }

  @Test
  void member_role_can_write_but_not_owner() {
    WorkspaceContext ctx = WorkspaceContext.personal(WORKSPACE_A, USER_A, WorkspaceRole.MEMBER);

    assertTrue(ctx.canWrite());
    assertFalse(ctx.isOwner());
  }
}

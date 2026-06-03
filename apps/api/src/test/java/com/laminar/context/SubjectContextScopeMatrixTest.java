package com.laminar.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laminar.subject.domain.SubjectRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 격리 매트릭스 5종 — SubjectContext API 레벨 (DB 통합은 Phase 4 인증 작업에 결합).
 *
 * <p>검증 매트릭스: 1. SYSTEM: system() 팩터리 → subjectId·userId null, scope == SYSTEM 2. SUBJECT_SHARED:
 * subject(wsId) → userId null, scope == SUBJECT_SHARED 3. PERSONAL/OWNER: personal(wsId, uId,
 * OWNER) → scope == PERSONAL, canWrite, isOwner 4. PERSONAL/VIEWER: personal(wsId, uId, VIEWER) →
 * scope == PERSONAL, !canWrite, !isOwner 5. invariant: subject(null) / personal(_, null) →
 * IllegalArgumentException
 *
 * <p>Hibernate Filter 동작 (cross-user/cross-subject SQL 누출 0건)은 Phase 4 통합 테스트에서 Testcontainers
 * PostgreSQL로 검증 — 본 unit은 scope 도출 규칙만 격리.
 */
class SubjectContextScopeMatrixTest {

  private static final UUID SUBJECT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SUBJECT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-000000000012");

  @Test
  void matrix_1_system_scope() {
    SubjectContext ctx = SubjectContext.system();

    assertEquals(SubjectContext.Scope.SYSTEM, ctx.scope());
    assertNull(ctx.subjectId());
    assertNull(ctx.userId());
    assertNull(ctx.userRole());
  }

  @Test
  void matrix_2_workspace_shared_scope() {
    SubjectContext ctx = SubjectContext.subject(SUBJECT_A);

    assertEquals(SubjectContext.Scope.SUBJECT_SHARED, ctx.scope());
    assertEquals(SUBJECT_A, ctx.subjectId());
    assertNull(ctx.userId());
  }

  @Test
  void matrix_3_personal_owner_full_write() {
    SubjectContext ctx = SubjectContext.personal(SUBJECT_A, USER_A, SubjectRole.OWNER);

    assertEquals(SubjectContext.Scope.PERSONAL, ctx.scope());
    assertEquals(SUBJECT_A, ctx.subjectId());
    assertEquals(USER_A, ctx.userId());
    assertTrue(ctx.isOwner());
    assertTrue(ctx.canWrite());
  }

  @Test
  void matrix_4_personal_viewer_read_only() {
    SubjectContext ctx = SubjectContext.personal(SUBJECT_B, USER_B, SubjectRole.VIEWER);

    assertEquals(SubjectContext.Scope.PERSONAL, ctx.scope());
    assertFalse(ctx.isOwner());
    assertFalse(ctx.canWrite());
  }

  @Test
  void matrix_5_invariant_violations_throw() {
    assertThrows(IllegalArgumentException.class, () -> SubjectContext.subject(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> SubjectContext.personal(null, USER_A, SubjectRole.MEMBER));
    assertThrows(
        IllegalArgumentException.class,
        () -> SubjectContext.personal(SUBJECT_A, null, SubjectRole.MEMBER));
  }

  @Test
  void member_role_can_write_but_not_owner() {
    SubjectContext ctx = SubjectContext.personal(SUBJECT_A, USER_A, SubjectRole.MEMBER);

    assertTrue(ctx.canWrite());
    assertFalse(ctx.isOwner());
  }
}

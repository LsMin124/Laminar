package com.laminar.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 격리 매트릭스 5종 — SubjectContext API 레벨 (DB 통합은 Phase 4 인증 작업에 결합).
 *
 * <p>검증 매트릭스: 1. SYSTEM: system() 팩터리 → subjectId·userId null, scope == SYSTEM 2. SUBJECT_SHARED:
 * subject(wsId) → userId null, scope == SUBJECT_SHARED 3. PERSONAL/OWNER: personal(wsId, uId,
 * OWNER) → scope == PERSONAL, canWrite, isOwner, isAdmin 4. PERSONAL/ADMIN: personal(wsId, uId,
 * ADMIN) → isAdmin이되 !isOwner (LAB재설계 §1.3 3등급) 5. invariant: subject(null) / personal(_, null) →
 * IllegalArgumentException 6. kind: 3-인자 personal은 PERSONAL 종별 기본, 4-인자 LAB만 isLab
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
    assertTrue(ctx.isAdmin());
    assertTrue(ctx.canWrite());
  }

  @Test
  void matrix_4_personal_admin_manages_but_not_owner() {
    SubjectContext ctx = SubjectContext.personal(SUBJECT_B, USER_B, SubjectRole.ADMIN);

    assertEquals(SubjectContext.Scope.PERSONAL, ctx.scope());
    assertFalse(ctx.isOwner());
    assertTrue(ctx.isAdmin());
    assertTrue(ctx.canWrite());
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
  void member_role_can_write_but_not_admin_nor_owner() {
    SubjectContext ctx = SubjectContext.personal(SUBJECT_A, USER_A, SubjectRole.MEMBER);

    assertTrue(ctx.canWrite());
    assertFalse(ctx.isAdmin());
    assertFalse(ctx.isOwner());
  }

  @Test
  void matrix_6_kind_defaults_personal_and_lab_flag() {
    assertFalse(SubjectContext.personal(SUBJECT_A, USER_A, SubjectRole.MEMBER).isLab());
    assertTrue(
        SubjectContext.personal(SUBJECT_A, USER_A, SubjectRole.MEMBER, SubjectKind.LAB).isLab());
    assertFalse(SubjectContext.system().isLab());
  }
}

package com.laminar.common.domain;

import java.time.OffsetDateTime;

/**
 * soft-delete 엔티티 표식 — {@code deleted_at IS NULL}이 "활성"인 엔티티가 구현한다(Lombok @Getter가 충족).
 *
 * <p>{@link com.laminar.common.repository.PersonalOwnedRepository}의 활성 필터가 이 인터페이스로 deletedAt에
 * 접근한다(DX-1②). 베이스 클래스에 두지 않는 이유: 모든 엔티티가 soft-delete를 쓰는 게 아니라서(예: audit_log는 append-only+보존삭제),
 * 능력(capability)은 선언적으로 표시한다.
 */
public interface SoftDeletable {

  OffsetDateTime getDeletedAt();
}

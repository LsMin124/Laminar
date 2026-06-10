package com.laminar.context;

import java.util.Optional;
import java.util.UUID;

/**
 * 활성 멤버십 해석 포트 (DX-16).
 *
 * <p>context는 14개 패키지가 의존하는 최기반 인프라 — 멤버십 저장소(subject 도메인)에 직접 의존하면 context↔subject 순환이 생기므로, 해석은 본
 * 인터페이스 너머로만 수행한다. 구현은 subject 도메인 측이 @Component로 제공.
 */
public interface MembershipResolver {

  /** subjectId×userId의 활성(미제거) 멤버십 역할. 비멤버면 empty (fail-closed). */
  Optional<SubjectRole> activeRole(UUID subjectId, UUID userId);
}

package com.laminar.subject.application;

import com.laminar.context.MembershipResolver;
import com.laminar.context.SubjectRole;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.repository.SubjectMemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link MembershipResolver}의 subject 도메인 구현 (DX-16).
 *
 * <p>SubjectContextRequestFilter가 포트 너머로 호출 — context 인프라가 subject 저장소를 모르게 유지한다.
 */
@Component
public class SubjectMembershipResolver implements MembershipResolver {

  private final SubjectMemberRepository memberRepo;

  public SubjectMembershipResolver(SubjectMemberRepository memberRepo) {
    this.memberRepo = memberRepo;
  }

  @Override
  public Optional<SubjectRole> activeRole(UUID subjectId, UUID userId) {
    return memberRepo
        .findByIdSubjectIdAndIdUserIdAndRemovedAtIsNull(subjectId, userId)
        .map(SubjectMemberEntity::getRole);
  }
}

package com.laminar.subject.application;

import com.laminar.context.MembershipResolver;
import com.laminar.subject.repository.SubjectMemberRepository;
import com.laminar.subject.repository.SubjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link MembershipResolver}의 subject 도메인 구현 (DX-16).
 *
 * <p>SubjectContextRequestFilter가 포트 너머로 호출 — context 인프라가 subject 저장소를 모르게 유지한다. 멤버 조회에 더해 주제
 * 종별(kind)·삭제 여부를 함께 해석한다: findById는 PK 로드라 @Filter가 적용되지 않아 컨텍스트 미확정 시점(본 해석이 바로 그 시점)에도 안전하다.
 */
@Component
public class SubjectMembershipResolver implements MembershipResolver {

  private final SubjectMemberRepository memberRepo;
  private final SubjectRepository subjectRepo;

  public SubjectMembershipResolver(
      SubjectMemberRepository memberRepo, SubjectRepository subjectRepo) {
    this.memberRepo = memberRepo;
    this.subjectRepo = subjectRepo;
  }

  @Override
  public Optional<Membership> activeMembership(UUID subjectId, UUID userId) {
    return memberRepo
        .findByIdSubjectIdAndIdUserIdAndRemovedAtIsNull(subjectId, userId)
        .flatMap(
            member ->
                subjectRepo
                    .findById(subjectId)
                    .filter(subject -> subject.getDeletedAt() == null)
                    .map(subject -> new Membership(member.getRole(), subject.getKind())));
  }
}

package com.laminar.subject.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectRole;
import com.laminar.error.ForbiddenException;
import com.laminar.error.NotFoundException;
import com.laminar.subject.domain.SubjectEntity;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.domain.SubjectMemberId;
import com.laminar.subject.repository.SubjectMemberRepository;
import com.laminar.subject.repository.SubjectRepository;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.domain.UserEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워크스페이스 멤버십 조회·관리 서비스.
 *
 * <p>멤버 응답에는 user email/displayName이 필요해 UserSystemRepository로 batch fetch. LAB재설계 §1.3: 역할 변경은
 * OWNER 전용(컨트롤러 가드), 제거는 ADMIN+이되 ADMIN은 MEMBER만(본 서비스 가드) + 원소유자·마지막 OWNER 보호.
 */
@Service
public class SubjectMemberService {

  private final SubjectMemberRepository memberRepo;
  private final UserSystemRepository userRepo;
  private final SubjectRepository subjectRepo;

  public SubjectMemberService(
      SubjectMemberRepository memberRepo,
      UserSystemRepository userRepo,
      SubjectRepository subjectRepo) {
    this.memberRepo = memberRepo;
    this.userRepo = userRepo;
    this.subjectRepo = subjectRepo;
  }

  @Transactional(readOnly = true)
  public List<MemberView> listCurrentMembers() {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.subjectId() == null) {
      throw new IllegalStateException("subject not selected");
    }
    List<SubjectMemberEntity> members =
        memberRepo.findByIdSubjectIdAndRemovedAtIsNull(ctx.subjectId());
    Map<UUID, UserEntity> userById =
        userRepo.findAllById(members.stream().map(m -> m.getId().getUserId()).toList()).stream()
            .collect(Collectors.toMap(UserEntity::getId, u -> u));
    return members.stream()
        .map(
            m -> {
              UserEntity user = userById.get(m.getId().getUserId());
              return new MemberView(
                  m.getId().getSubjectId(),
                  m.getId().getUserId(),
                  user == null ? null : user.getEmail(),
                  user == null ? null : user.getDisplayName(),
                  m.getRole(),
                  m.getJoinedAt());
            })
        .toList();
  }

  @Transactional
  public SubjectMemberEntity updateRole(UUID userId, SubjectRole newRole, UUID actorId) {
    SubjectContext ctx = SubjectContextHolder.require();
    // Q3: 인가 정본은 서비스 계층 — 컨트롤러 게이트를 우회하는 경로에서도 OWNER 전용을 강제(§1.3).
    if (!ctx.isOwner()) {
      throw new ForbiddenException("역할 변경은 OWNER만 가능합니다");
    }
    SubjectMemberId id = new SubjectMemberId(ctx.subjectId(), userId);
    SubjectMemberEntity member =
        memberRepo.findById(id).orElseThrow(() -> new NotFoundException("member not found"));
    if (member.getRemovedAt() != null) {
      throw new IllegalArgumentException("member already removed");
    }
    if (userId.equals(actorId) && newRole != SubjectRole.OWNER) {
      ensureAnotherOwnerExists(ctx.subjectId(), userId);
    }
    member.setRole(newRole);
    return memberRepo.save(member);
  }

  @Transactional
  public void removeMember(UUID userId, UUID actorId) {
    SubjectContext ctx = SubjectContextHolder.require();
    SubjectEntity subject =
        subjectRepo
            .findById(ctx.subjectId())
            .orElseThrow(() -> new IllegalStateException("subject not found"));
    if (subject.getOwnerUserId().equals(userId)) {
      throw new IllegalArgumentException("subject owner cannot be removed");
    }
    if (userId.equals(actorId)) {
      ensureAnotherOwnerExists(ctx.subjectId(), userId);
    }
    SubjectMemberId id = new SubjectMemberId(ctx.subjectId(), userId);
    SubjectMemberEntity member =
        memberRepo.findById(id).orElseThrow(() -> new NotFoundException("member not found"));
    if (member.getRemovedAt() != null) {
      return;
    }
    // §1.3 차등: ADMIN은 MEMBER만 제거 가능 — OWNER·ADMIN 제거는 OWNER 전용.
    if (!ctx.isOwner() && member.getRole() != SubjectRole.MEMBER) {
      throw new ForbiddenException("ADMIN can remove MEMBER only");
    }
    member.setRemovedAt(OffsetDateTime.now());
    memberRepo.save(member);
  }

  private void ensureAnotherOwnerExists(UUID subjectId, UUID excludingUserId) {
    boolean hasOther =
        memberRepo.findByIdSubjectIdAndRemovedAtIsNull(subjectId).stream()
            .anyMatch(
                m ->
                    m.getRole() == SubjectRole.OWNER
                        && !m.getId().getUserId().equals(excludingUserId));
    if (!hasOther) {
      throw new IllegalArgumentException("last OWNER cannot be downgraded or removed");
    }
  }

  public record MemberView(
      UUID subjectId,
      UUID userId,
      String email,
      String displayName,
      SubjectRole role,
      OffsetDateTime joinedAt) {}
}

package com.laminar.subject.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectRole;
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
 * <p>멤버 응답에는 user email/displayName이 필요해 UserSystemRepository로 batch fetch. 역할 변경·강퇴는 OWNER만
 * (canWrite + 자기 자신 보호).
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

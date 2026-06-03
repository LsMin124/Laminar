package com.laminar.workspace.application;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.UserEntity;
import com.laminar.workspace.domain.WorkspaceEntity;
import com.laminar.workspace.domain.WorkspaceMemberEntity;
import com.laminar.workspace.domain.WorkspaceMemberId;
import com.laminar.workspace.domain.WorkspaceRole;
import com.laminar.workspace.repository.WorkspaceMemberRepository;
import com.laminar.workspace.repository.WorkspaceRepository;
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
public class WorkspaceMemberService {

  private final WorkspaceMemberRepository memberRepo;
  private final UserSystemRepository userRepo;
  private final WorkspaceRepository workspaceRepo;

  public WorkspaceMemberService(
      WorkspaceMemberRepository memberRepo,
      UserSystemRepository userRepo,
      WorkspaceRepository workspaceRepo) {
    this.memberRepo = memberRepo;
    this.userRepo = userRepo;
    this.workspaceRepo = workspaceRepo;
  }

  @Transactional(readOnly = true)
  public List<MemberView> listCurrentMembers() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.workspaceId() == null) {
      throw new IllegalStateException("workspace not selected");
    }
    List<WorkspaceMemberEntity> members =
        memberRepo.findByIdWorkspaceIdAndRemovedAtIsNull(ctx.workspaceId());
    Map<UUID, UserEntity> userById =
        userRepo.findAllById(members.stream().map(m -> m.getId().getUserId()).toList()).stream()
            .collect(Collectors.toMap(UserEntity::getId, u -> u));
    return members.stream()
        .map(
            m -> {
              UserEntity user = userById.get(m.getId().getUserId());
              return new MemberView(
                  m.getId().getWorkspaceId(),
                  m.getId().getUserId(),
                  user == null ? null : user.getEmail(),
                  user == null ? null : user.getDisplayName(),
                  m.getRole(),
                  m.getJoinedAt());
            })
        .toList();
  }

  @Transactional
  public WorkspaceMemberEntity updateRole(UUID userId, WorkspaceRole newRole, UUID actorId) {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    WorkspaceMemberId id = new WorkspaceMemberId(ctx.workspaceId(), userId);
    WorkspaceMemberEntity member =
        memberRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("member not found"));
    if (member.getRemovedAt() != null) {
      throw new IllegalArgumentException("member already removed");
    }
    if (userId.equals(actorId) && newRole != WorkspaceRole.OWNER) {
      ensureAnotherOwnerExists(ctx.workspaceId(), userId);
    }
    member.setRole(newRole);
    return memberRepo.save(member);
  }

  @Transactional
  public void removeMember(UUID userId, UUID actorId) {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    WorkspaceEntity workspace =
        workspaceRepo
            .findById(ctx.workspaceId())
            .orElseThrow(() -> new IllegalStateException("workspace not found"));
    if (workspace.getOwnerUserId().equals(userId)) {
      throw new IllegalArgumentException("workspace owner cannot be removed");
    }
    if (userId.equals(actorId)) {
      ensureAnotherOwnerExists(ctx.workspaceId(), userId);
    }
    WorkspaceMemberId id = new WorkspaceMemberId(ctx.workspaceId(), userId);
    WorkspaceMemberEntity member =
        memberRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("member not found"));
    if (member.getRemovedAt() != null) {
      return;
    }
    member.setRemovedAt(OffsetDateTime.now());
    memberRepo.save(member);
  }

  private void ensureAnotherOwnerExists(UUID workspaceId, UUID excludingUserId) {
    boolean hasOther =
        memberRepo.findByIdWorkspaceIdAndRemovedAtIsNull(workspaceId).stream()
            .anyMatch(
                m ->
                    m.getRole() == WorkspaceRole.OWNER
                        && !m.getId().getUserId().equals(excludingUserId));
    if (!hasOther) {
      throw new IllegalArgumentException("last OWNER cannot be downgraded or removed");
    }
  }

  public record MemberView(
      UUID workspaceId,
      UUID userId,
      String email,
      String displayName,
      WorkspaceRole role,
      OffsetDateTime joinedAt) {}
}

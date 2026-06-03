package com.laminar.workspace;

import com.laminar.context.WorkspaceContextHolder;
import com.laminar.outbox.domain.EmailOutboxEntity;
import com.laminar.system.EmailOutboxSystemRepository;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.UserEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워크스페이스 멤버 초대 (7일 TTL, 단일 사용 토큰).
 *
 * <p>흐름: 1. invite(): SecureRandom 32B raw token + SHA-256 hash 저장 + email_outbox 큐 INSERT 2.
 * accept(rawToken): hash 매칭 + 만료/revoked 체크 + 멤버 INSERT (사용자 미존재 시 빈 Optional) 3. revoke():
 * revoked_at 마킹 (hard delete 아님)
 */
@Service
public class InvitationService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;
  private static final int TTL_DAYS = 7;

  private final WorkspaceInvitationRepository invitationRepo;
  private final WorkspaceMemberRepository memberRepo;
  private final UserSystemRepository userRepo;
  private final EmailOutboxSystemRepository emailOutboxRepo;

  public InvitationService(
      WorkspaceInvitationRepository invitationRepo,
      WorkspaceMemberRepository memberRepo,
      UserSystemRepository userRepo,
      EmailOutboxSystemRepository emailOutboxRepo) {
    this.invitationRepo = invitationRepo;
    this.memberRepo = memberRepo;
    this.userRepo = userRepo;
    this.emailOutboxRepo = emailOutboxRepo;
  }

  @Transactional
  public InvitationIssue invite(
      UUID workspaceId, String email, WorkspaceRole role, UUID invitedBy) {
    String normalizedEmail = email.trim().toLowerCase();
    String rawToken = generateRawToken();
    String tokenHash = sha256(rawToken);

    WorkspaceInvitationEntity invitation = new WorkspaceInvitationEntity();
    invitation.setWorkspaceId(workspaceId);
    invitation.setEmail(normalizedEmail);
    invitation.setRole(role);
    invitation.setInvitedBy(invitedBy);
    invitation.setTokenHash(tokenHash);
    invitation.setExpiresAt(OffsetDateTime.now().plus(TTL_DAYS, ChronoUnit.DAYS));
    WorkspaceInvitationEntity saved = invitationRepo.save(invitation);

    enqueueInvitationEmail(normalizedEmail, rawToken);
    return new InvitationIssue(saved.getId(), rawToken);
  }

  @Transactional
  public Optional<WorkspaceMemberEntity> accept(String rawToken) {
    Optional<WorkspaceInvitationEntity> maybeInvite =
        invitationRepo
            .findByTokenHash(sha256(rawToken))
            .filter(i -> i.getAcceptedAt() == null)
            .filter(i -> i.getRevokedAt() == null)
            .filter(i -> i.getExpiresAt().isAfter(OffsetDateTime.now()));
    if (maybeInvite.isEmpty()) return Optional.empty();
    WorkspaceInvitationEntity invitation = maybeInvite.get();

    Optional<UserEntity> maybeUser = userRepo.findByEmailAndDeletedAtIsNull(invitation.getEmail());
    if (maybeUser.isEmpty()) {
      return Optional.empty();
    }

    WorkspaceMemberEntity member = new WorkspaceMemberEntity();
    member.setId(new WorkspaceMemberId(invitation.getWorkspaceId(), maybeUser.get().getId()));
    member.setRole(invitation.getRole());
    WorkspaceMemberEntity saved = memberRepo.save(member);

    invitation.setAcceptedAt(OffsetDateTime.now());
    invitationRepo.save(invitation);
    return Optional.of(saved);
  }

  @Transactional
  public void revoke(UUID invitationId) {
    invitationRepo
        .findById(invitationId)
        .ifPresent(
            invitation -> {
              if (invitation.getRevokedAt() == null) {
                invitation.setRevokedAt(OffsetDateTime.now());
                invitationRepo.save(invitation);
              }
            });
  }

  @Transactional(readOnly = true)
  public java.util.List<WorkspaceInvitationEntity> listPendingForCurrentWorkspace() {
    UUID workspaceId = WorkspaceContextHolder.require().workspaceId();
    if (workspaceId == null) {
      throw new IllegalStateException("workspace context required");
    }
    return invitationRepo.findByWorkspaceIdAndAcceptedAtIsNullAndRevokedAtIsNull(workspaceId);
  }

  private void enqueueInvitationEmail(String recipientEmail, String rawToken) {
    EmailOutboxEntity email = new EmailOutboxEntity();
    email.setToEmail(recipientEmail);
    email.setSubject("Laminar 워크스페이스 초대");
    email.setTemplateKey("workspace.invitation");
    String workspaceName =
        WorkspaceContextHolder.get() == null
            ? "워크스페이스"
            : "워크스페이스(" + WorkspaceContextHolder.get().workspaceId() + ")";
    email.setBodyText("초대 토큰: " + rawToken + "\n7일 이내 수락하세요. (" + workspaceName + ")");
    emailOutboxRepo.save(email);
  }

  private static String generateRawToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable in JVM — environment broken", e);
    }
  }

  public record InvitationIssue(UUID invitationId, String rawToken) {}
}

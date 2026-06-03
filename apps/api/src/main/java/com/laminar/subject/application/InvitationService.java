package com.laminar.subject.application;

import com.laminar.context.SubjectContextHolder;
import com.laminar.outbox.domain.EmailOutboxEntity;
import com.laminar.subject.domain.SubjectInvitationEntity;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.domain.SubjectMemberId;
import com.laminar.subject.domain.SubjectRole;
import com.laminar.subject.repository.SubjectInvitationRepository;
import com.laminar.subject.repository.SubjectMemberRepository;
import com.laminar.system.EmailOutboxSystemRepository;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.domain.UserEntity;
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

  private final SubjectInvitationRepository invitationRepo;
  private final SubjectMemberRepository memberRepo;
  private final UserSystemRepository userRepo;
  private final EmailOutboxSystemRepository emailOutboxRepo;

  public InvitationService(
      SubjectInvitationRepository invitationRepo,
      SubjectMemberRepository memberRepo,
      UserSystemRepository userRepo,
      EmailOutboxSystemRepository emailOutboxRepo) {
    this.invitationRepo = invitationRepo;
    this.memberRepo = memberRepo;
    this.userRepo = userRepo;
    this.emailOutboxRepo = emailOutboxRepo;
  }

  @Transactional
  public InvitationIssue invite(UUID subjectId, String email, SubjectRole role, UUID invitedBy) {
    String normalizedEmail = email.trim().toLowerCase();
    String rawToken = generateRawToken();
    String tokenHash = sha256(rawToken);

    SubjectInvitationEntity invitation = new SubjectInvitationEntity();
    invitation.setSubjectId(subjectId);
    invitation.setEmail(normalizedEmail);
    invitation.setRole(role);
    invitation.setInvitedBy(invitedBy);
    invitation.setTokenHash(tokenHash);
    invitation.setExpiresAt(OffsetDateTime.now().plus(TTL_DAYS, ChronoUnit.DAYS));
    SubjectInvitationEntity saved = invitationRepo.save(invitation);

    enqueueInvitationEmail(normalizedEmail, rawToken);
    return new InvitationIssue(saved.getId(), rawToken);
  }

  @Transactional
  public Optional<SubjectMemberEntity> accept(String rawToken) {
    Optional<SubjectInvitationEntity> maybeInvite =
        invitationRepo
            .findByTokenHash(sha256(rawToken))
            .filter(i -> i.getAcceptedAt() == null)
            .filter(i -> i.getRevokedAt() == null)
            .filter(i -> i.getExpiresAt().isAfter(OffsetDateTime.now()));
    if (maybeInvite.isEmpty()) return Optional.empty();
    SubjectInvitationEntity invitation = maybeInvite.get();

    Optional<UserEntity> maybeUser = userRepo.findByEmailAndDeletedAtIsNull(invitation.getEmail());
    if (maybeUser.isEmpty()) {
      return Optional.empty();
    }

    SubjectMemberEntity member = new SubjectMemberEntity();
    member.setId(new SubjectMemberId(invitation.getSubjectId(), maybeUser.get().getId()));
    member.setRole(invitation.getRole());
    SubjectMemberEntity saved = memberRepo.save(member);

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
  public java.util.List<SubjectInvitationEntity> listPendingForCurrentSubject() {
    UUID subjectId = SubjectContextHolder.require().subjectId();
    if (subjectId == null) {
      throw new IllegalStateException("subject context required");
    }
    return invitationRepo.findBySubjectIdAndAcceptedAtIsNullAndRevokedAtIsNull(subjectId);
  }

  private void enqueueInvitationEmail(String recipientEmail, String rawToken) {
    EmailOutboxEntity email = new EmailOutboxEntity();
    email.setToEmail(recipientEmail);
    email.setSubject("Laminar 워크스페이스 초대");
    email.setTemplateKey("subject.invitation");
    String subjectName =
        SubjectContextHolder.get() == null
            ? "워크스페이스"
            : "워크스페이스(" + SubjectContextHolder.get().subjectId() + ")";
    email.setBodyText("초대 토큰: " + rawToken + "\n7일 이내 수락하세요. (" + subjectName + ")");
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

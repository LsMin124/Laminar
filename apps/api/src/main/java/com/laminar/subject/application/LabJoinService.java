package com.laminar.subject.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectKind;
import com.laminar.context.SubjectRole;
import com.laminar.error.ConflictException;
import com.laminar.error.NotFoundException;
import com.laminar.subject.domain.LabInviteCodeEntity;
import com.laminar.subject.domain.LabJoinRequestEntity;
import com.laminar.subject.domain.LabJoinStatus;
import com.laminar.subject.domain.SubjectEntity;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.domain.SubjectMemberId;
import com.laminar.subject.repository.LabInviteCodeRepository;
import com.laminar.subject.repository.LabJoinRequestRepository;
import com.laminar.subject.repository.SubjectMemberRepository;
import com.laminar.subject.repository.SubjectRepository;
import com.laminar.system.LabInviteCodeSystemRepository;
import com.laminar.system.LabJoinRequestSystemRepository;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.domain.UserEntity;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LAB 가입 흐름 (LAB재설계 §2) — 초대코드 발급/회전(ADMIN+) · 코드 입력 가입 신청(비멤버, SYSTEM scope) · 승인/거절(ADMIN+).
 *
 * <p>이메일 초대는 기존 InvitationService(발급=승인 내재)가 담당 — 본 서비스는 코드 경로(공유 가능해 명시 승인 필수)만. 승인 시 멤버는 MEMBER
 * 역할로 INSERT(과거 제거된 멤버는 재활성화), 승급은 OWNER의 역할 변경으로만.
 */
@Service
public class LabJoinService {

  private static final SecureRandom RANDOM = new SecureRandom();
  // 사람이 입력하는 코드 — 혼동 문자(I, O, 0, 1) 제외 32자 알파벳, 8자리(≈10^12 조합 + 레이트리밋).
  private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int CODE_LENGTH = 8;

  private final LabInviteCodeRepository codeRepo;
  private final LabInviteCodeSystemRepository codeSystemRepo;
  private final LabJoinRequestRepository requestRepo;
  private final LabJoinRequestSystemRepository requestSystemRepo;
  private final SubjectMemberRepository memberRepo;
  private final SubjectRepository subjectRepo;
  private final UserSystemRepository userRepo;

  public LabJoinService(
      LabInviteCodeRepository codeRepo,
      LabInviteCodeSystemRepository codeSystemRepo,
      LabJoinRequestRepository requestRepo,
      LabJoinRequestSystemRepository requestSystemRepo,
      SubjectMemberRepository memberRepo,
      SubjectRepository subjectRepo,
      UserSystemRepository userRepo) {
    this.codeRepo = codeRepo;
    this.codeSystemRepo = codeSystemRepo;
    this.requestRepo = requestRepo;
    this.requestSystemRepo = requestSystemRepo;
    this.memberRepo = memberRepo;
    this.subjectRepo = subjectRepo;
    this.userRepo = userRepo;
  }

  // ---------- 관리자 측 (lab PERSONAL 컨텍스트) ----------

  /** 초대코드 발급/회전 — 기존 활성 코드는 revoke(유출 대응 정본), 새 코드 반환. */
  @Transactional
  public LabInviteCodeEntity rotateInviteCode(UUID actorId) {
    SubjectContext ctx = requireLabAdmin();
    codeRepo
        .findBySubjectIdAndRevokedAtIsNull(ctx.subjectId())
        .ifPresent(
            existing -> {
              existing.setRevokedAt(OffsetDateTime.now());
              codeRepo.save(existing);
            });
    LabInviteCodeEntity fresh = new LabInviteCodeEntity();
    fresh.setSubjectId(ctx.subjectId());
    fresh.setCode(generateCode());
    fresh.setCreatedBy(actorId);
    return codeRepo.save(fresh);
  }

  /** 현재 활성 초대코드 — 미발급이면 empty. */
  @Transactional(readOnly = true)
  public Optional<LabInviteCodeEntity> currentInviteCode() {
    SubjectContext ctx = requireLabAdmin();
    return codeRepo.findBySubjectIdAndRevokedAtIsNull(ctx.subjectId());
  }

  /** 대기 중 가입 신청 목록 — 신청자 email/displayName을 batch fetch해 뷰로 반환. */
  @Transactional(readOnly = true)
  public List<JoinRequestView> listPending() {
    SubjectContext ctx = requireLabAdmin();
    List<LabJoinRequestEntity> requests =
        requestRepo.findBySubjectIdAndStatusOrderByCreatedAtAsc(
            ctx.subjectId(), LabJoinStatus.PENDING);
    Map<UUID, UserEntity> userById =
        userRepo
            .findAllById(requests.stream().map(LabJoinRequestEntity::getUserId).toList())
            .stream()
            .collect(Collectors.toMap(UserEntity::getId, u -> u));
    return requests.stream()
        .map(
            r -> {
              UserEntity user = userById.get(r.getUserId());
              return new JoinRequestView(
                  r.getId(),
                  r.getUserId(),
                  user == null ? null : user.getEmail(),
                  user == null ? null : user.getDisplayName(),
                  r.getCreatedAt());
            })
        .toList();
  }

  /** 가입 승인 — MEMBER 역할로 멤버 INSERT(과거 제거 멤버는 재활성화) + 신청 approved 마킹. */
  @Transactional
  public void approve(UUID requestId, UUID actorId) {
    decide(requestId, actorId, true);
  }

  /** 가입 거절 — 신청 rejected 마킹(재신청 가능). */
  @Transactional
  public void reject(UUID requestId, UUID actorId) {
    decide(requestId, actorId, false);
  }

  private void decide(UUID requestId, UUID actorId, boolean approve) {
    SubjectContext ctx = requireLabAdmin();
    LabJoinRequestEntity request =
        requestRepo
            .findById(requestId)
            // PK 로드는 @Filter 비적용 — 명시 소유 검증 (fail-closed)
            .filter(r -> ctx.ownsShared(r.getSubjectId()))
            .orElseThrow(() -> new NotFoundException("join request not found"));
    if (request.getStatus() != LabJoinStatus.PENDING) {
      throw new ConflictException("이미 처리된 신청입니다.");
    }
    if (approve) {
      SubjectMemberId memberId = new SubjectMemberId(request.getSubjectId(), request.getUserId());
      SubjectMemberEntity member =
          memberRepo
              .findById(memberId)
              .orElseGet(
                  () -> {
                    SubjectMemberEntity fresh = new SubjectMemberEntity();
                    fresh.setId(memberId);
                    return fresh;
                  });
      member.setRole(SubjectRole.MEMBER);
      member.setRemovedAt(null); // 과거 제거된 멤버의 재가입 — 재활성화
      memberRepo.save(member);
      request.setStatus(LabJoinStatus.APPROVED);
    } else {
      request.setStatus(LabJoinStatus.REJECTED);
    }
    request.setDecidedBy(actorId);
    request.setDecidedAt(OffsetDateTime.now());
    requestRepo.save(request);
  }

  // ---------- 신청자 측 (SYSTEM scope — 비멤버라 subject 헤더 없이 진입) ----------

  /**
   * 초대코드로 가입 신청 — pending 생성. 무효/회수 코드·삭제된 주제·lab 아님은 동일 404(코드 유효성 노출 최소화), 이미 활성 멤버면 409, 기존
   * pending은 멱등 반환.
   */
  @Transactional
  public JoinOutcome join(String rawCode, UUID userId) {
    if (rawCode == null || rawCode.isBlank()) {
      throw new IllegalArgumentException("초대코드를 입력하세요.");
    }
    String code = rawCode.trim().toUpperCase();
    LabInviteCodeEntity invite =
        codeSystemRepo
            .findByCodeAndRevokedAtIsNull(code)
            .orElseThrow(() -> new NotFoundException("유효하지 않은 초대코드입니다."));
    SubjectEntity lab =
        subjectRepo
            .findById(invite.getSubjectId())
            .filter(s -> s.getDeletedAt() == null)
            .filter(s -> s.getKind() == SubjectKind.LAB)
            .orElseThrow(() -> new NotFoundException("유효하지 않은 초대코드입니다."));

    boolean activeMember =
        memberRepo
            .findById(new SubjectMemberId(lab.getId(), userId))
            .filter(m -> m.getRemovedAt() == null)
            .isPresent();
    if (activeMember) {
      throw new ConflictException("이미 이 LAB의 멤버입니다.");
    }

    Optional<LabJoinRequestEntity> pending =
        requestSystemRepo.findBySubjectIdAndUserIdAndStatus(
            lab.getId(), userId, LabJoinStatus.PENDING);
    if (pending.isPresent()) {
      return new JoinOutcome(lab.getId(), lab.getName(), pending.get().getStatus()); // 멱등
    }

    LabJoinRequestEntity request = new LabJoinRequestEntity();
    request.setSubjectId(lab.getId());
    request.setUserId(userId);
    request.setStatus(LabJoinStatus.PENDING);
    requestSystemRepo.save(request);
    return new JoinOutcome(lab.getId(), lab.getName(), LabJoinStatus.PENDING);
  }

  /** lab 관리 가드 — SubjectContextHolder 정본 위임 (L3에서 장비 서비스와 통합). */
  private SubjectContext requireLabAdmin() {
    return SubjectContextHolder.requireLabAdmin("lab join");
  }

  private static String generateCode() {
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
    }
    return sb.toString();
  }

  public record JoinRequestView(
      UUID id, UUID userId, String email, String displayName, OffsetDateTime requestedAt) {}

  public record JoinOutcome(UUID labId, String labName, LabJoinStatus status) {}
}

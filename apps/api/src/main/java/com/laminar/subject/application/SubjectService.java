package com.laminar.subject.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.subject.domain.SubjectEntity;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.domain.SubjectMemberId;
import com.laminar.subject.domain.SubjectRole;
import com.laminar.subject.repository.SubjectMemberRepository;
import com.laminar.subject.repository.SubjectRepository;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워크스페이스 도메인 서비스.
 *
 * <p>가입 직후 personal subject 자동 생성 + owner 멤버십 INSERT — 단일 트랜잭션. slug 충돌 시 -2, -3 등 suffix 자동 부여 (최대
 * 5회 retry, 이후 IllegalStateException).
 */
@Service
public class SubjectService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int MAX_SLUG_RETRY = 5;

  private final SubjectRepository subjectRepo;
  private final SubjectMemberRepository memberRepo;

  public SubjectService(SubjectRepository subjectRepo, SubjectMemberRepository memberRepo) {
    this.subjectRepo = subjectRepo;
    this.memberRepo = memberRepo;
  }

  /** 가입 직후 호출 — displayName 기반 slug + owner 멤버십까지 한 번에. */
  @Transactional
  public SubjectEntity createPersonalSubject(UUID userId, String displayName) {
    String baseSlug = slugify(displayName);
    SubjectEntity subject = new SubjectEntity();
    subject.setName(displayName + "의 워크스페이스");
    subject.setSlug(resolveUniqueSlug(baseSlug));
    subject.setOwnerUserId(userId);
    subject.setDefaultTimezone("Asia/Seoul");
    subject.setSettings(new HashMap<>());
    SubjectEntity saved = subjectRepo.save(subject);

    SubjectMemberEntity owner = new SubjectMemberEntity();
    owner.setId(new SubjectMemberId(saved.getId(), userId));
    owner.setRole(SubjectRole.OWNER);
    memberRepo.save(owner);

    return saved;
  }

  @Transactional
  public SubjectEntity create(UUID ownerUserId, String name, String slug, String timezone) {
    SubjectEntity subject = new SubjectEntity();
    subject.setName(name);
    subject.setSlug(resolveUniqueSlug(slug));
    subject.setOwnerUserId(ownerUserId);
    subject.setDefaultTimezone(timezone == null ? "Asia/Seoul" : timezone);
    subject.setSettings(new HashMap<>());
    SubjectEntity saved = subjectRepo.save(subject);

    SubjectMemberEntity owner = new SubjectMemberEntity();
    owner.setId(new SubjectMemberId(saved.getId(), ownerUserId));
    owner.setRole(SubjectRole.OWNER);
    memberRepo.save(owner);

    return saved;
  }

  @Transactional(readOnly = true)
  public SubjectEntity requireCurrent() {
    SubjectContext context = SubjectContextHolder.require();
    if (context.scope() == SubjectContext.Scope.SYSTEM) {
      throw new IllegalStateException("subject not selected");
    }
    return subjectRepo
        .findById(context.subjectId())
        .orElseThrow(() -> new IllegalStateException("subject not found in context"));
  }

  @Transactional(readOnly = true)
  public List<SubjectMemberEntity> listActiveMembershipsForUser(UUID userId) {
    return memberRepo.findByIdSubjectIdAndRemovedAtIsNull(
        SubjectContextHolder.require().subjectId());
  }

  /**
   * 인증 사용자가 속한 모든 워크스페이스 — 가입 직후 SYSTEM scope에서 워크스페이스 발견용. 워크스페이스 헤더 없이 호출되며 principal.userId
   * 기준으로만 조회(타인 노출 없음).
   */
  @Transactional(readOnly = true)
  public List<SubjectEntity> listForUser(UUID userId) {
    List<UUID> subjectIds =
        memberRepo.findAllByIdUserIdAndRemovedAtIsNull(userId).stream()
            .map(m -> m.getId().getSubjectId())
            .toList();
    return subjectRepo.findAllById(subjectIds);
  }

  @Transactional
  public SubjectEntity updateCurrent(
      String name, String timezone, String bodyMd, Map<String, Object> settings) {
    SubjectEntity subject = requireCurrent();
    if (name != null && !name.isBlank()) {
      subject.setName(name);
    }
    if (timezone != null && !timezone.isBlank()) {
      subject.setDefaultTimezone(timezone);
    }
    if (bodyMd != null) {
      subject.setBodyMd(bodyMd);
    }
    if (settings != null) {
      subject.setSettings(settings);
    }
    return subjectRepo.save(subject);
  }

  /**
   * 현재(활성) 주제 영구 삭제 — 소유자만. 자식(탭·카드·관계·그룹·멤버 등)은 FK ON DELETE CASCADE로 DB에서 함께 제거된다. 관례상 소유권 위반은
   * IllegalStateException → 403.
   */
  @Transactional
  public void deleteCurrent(UUID userId) {
    SubjectEntity subject = requireCurrent();
    if (!subject.getOwnerUserId().equals(userId)) {
      throw new IllegalStateException("only owner can delete subject");
    }
    subjectRepo.delete(subject);
  }

  private String resolveUniqueSlug(String baseSlug) {
    String candidate = baseSlug;
    for (int attempt = 1; attempt <= MAX_SLUG_RETRY; attempt++) {
      if (subjectRepo.findBySlug(candidate).isEmpty()) {
        return candidate;
      }
      candidate = baseSlug + "-" + (RANDOM.nextInt(900) + 100);
    }
    throw new IllegalStateException("slug collision exhausted retries: " + baseSlug);
  }

  static String slugify(String input) {
    if (input == null || input.isBlank()) {
      return "ws-" + (RANDOM.nextInt(9000) + 1000);
    }
    String lower = input.trim().toLowerCase();
    String ascii = lower.replaceAll("[^a-z0-9가-힣\\-]+", "-");
    String trimmed = ascii.replaceAll("^-+|-+$", "");
    if (trimmed.length() > 40) {
      trimmed = trimmed.substring(0, 40);
    }
    return trimmed.isEmpty() ? "ws-" + (RANDOM.nextInt(9000) + 1000) : trimmed;
  }
}

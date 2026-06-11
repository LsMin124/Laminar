package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.context.MembershipResolver;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectKind;
import com.laminar.context.SubjectRole;
import com.laminar.error.ForbiddenException;
import com.laminar.subject.application.SubjectService;
import com.laminar.subject.domain.SubjectEntity;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.domain.SubjectMemberId;
import com.laminar.subject.repository.SubjectMemberRepository;
import com.laminar.subject.repository.SubjectRepository;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.domain.UserEntity;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** LAB 승격 회귀 (L1, LAB재설계 §1.1) — 신규 주제는 personal 기본, 승격은 OWNER 전용·멱등, 리졸버가 kind를 컨텍스트로 전달. */
class LabPromotionIT extends IsolationIntegrationBase {

  @Autowired SubjectService subjectService;
  @Autowired MembershipResolver membershipResolver;
  @Autowired UserSystemRepository userRepo;
  @Autowired SubjectRepository subjectRepo;
  @Autowired SubjectMemberRepository memberRepo;

  private UUID subjectId;
  private UUID ownerUser;
  private UUID memberUser;

  @BeforeEach
  void seed() {
    SubjectContextHolder.clear();
    ownerUser = seedUser("lab-owner");
    memberUser = seedUser("lab-member");

    SubjectEntity subject = new SubjectEntity();
    subject.setName("Lab Candidate");
    subject.setSlug("lab-cand-" + UUID.randomUUID());
    subject.setOwnerUserId(ownerUser);
    subject.setDefaultTimezone("Asia/Seoul");
    subject.setSettings(new HashMap<>());
    subjectId = subjectRepo.save(subject).getId();

    seedMember(ownerUser, SubjectRole.OWNER);
    seedMember(memberUser, SubjectRole.MEMBER);
  }

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  @Transactional
  void new_subject_defaults_to_personal_kind() {
    assertThat(subjectRepo.findById(subjectId).orElseThrow().getKind())
        .isEqualTo(SubjectKind.PERSONAL);
    assertThat(membershipResolver.activeMembership(subjectId, ownerUser))
        .hasValueSatisfying(
            m -> {
              assertThat(m.role()).isEqualTo(SubjectRole.OWNER);
              assertThat(m.kind()).isEqualTo(SubjectKind.PERSONAL);
            });
  }

  @Test
  @Transactional
  void owner_promotes_to_lab_idempotently_and_resolver_reflects_kind() {
    SubjectContextHolder.set(SubjectContext.personal(subjectId, ownerUser, SubjectRole.OWNER));

    SubjectEntity promoted = subjectService.promoteCurrentToLab();
    assertThat(promoted.getKind()).isEqualTo(SubjectKind.LAB);

    // 멱등 — 재호출도 LAB 유지, 예외 없음
    assertThat(subjectService.promoteCurrentToLab().getKind()).isEqualTo(SubjectKind.LAB);

    assertThat(membershipResolver.activeMembership(subjectId, memberUser))
        .hasValueSatisfying(
            m -> {
              assertThat(m.role()).isEqualTo(SubjectRole.MEMBER);
              assertThat(m.kind()).isEqualTo(SubjectKind.LAB);
            });
  }

  @Test
  @Transactional
  void non_owner_cannot_promote() {
    SubjectContextHolder.set(SubjectContext.personal(subjectId, memberUser, SubjectRole.MEMBER));

    assertThatThrownBy(() -> subjectService.promoteCurrentToLab())
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("OWNER");
    assertThat(subjectRepo.findById(subjectId).orElseThrow().getKind())
        .isEqualTo(SubjectKind.PERSONAL);
  }

  private UUID seedUser(String prefix) {
    UserEntity user = new UserEntity();
    user.setEmail(prefix + "-" + UUID.randomUUID() + "@test.local");
    return userRepo.save(user).getId();
  }

  private void seedMember(UUID userId, SubjectRole role) {
    SubjectMemberEntity member = new SubjectMemberEntity();
    member.setId(new SubjectMemberId(subjectId, userId));
    member.setRole(role);
    memberRepo.save(member);
  }
}

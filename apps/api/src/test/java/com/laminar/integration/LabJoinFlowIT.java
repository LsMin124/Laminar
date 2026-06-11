package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectKind;
import com.laminar.context.SubjectRole;
import com.laminar.error.ConflictException;
import com.laminar.error.ForbiddenException;
import com.laminar.error.NotFoundException;
import com.laminar.subject.application.LabJoinService;
import com.laminar.subject.domain.LabJoinStatus;
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

/**
 * LAB 가입 흐름 통합 회귀 (L2, LAB재설계 §2) — 코드 발급(ADMIN+)/회전, 코드 가입 신청(SYSTEM scope)·멱등, 승인→MEMBER
 * INSERT/거절, 권한 차단.
 */
class LabJoinFlowIT extends IsolationIntegrationBase {

  @Autowired LabJoinService labJoinService;
  @Autowired UserSystemRepository userRepo;
  @Autowired SubjectRepository subjectRepo;
  @Autowired SubjectMemberRepository memberRepo;

  private UUID labId;
  private UUID ownerUser;
  private UUID memberUser;
  private UUID applicantUser;

  @BeforeEach
  void seed() {
    SubjectContextHolder.clear();
    ownerUser = seedUser("join-owner");
    memberUser = seedUser("join-member");
    applicantUser = seedUser("join-applicant");

    SubjectEntity lab = new SubjectEntity();
    lab.setName("Join Lab");
    lab.setSlug("join-lab-" + UUID.randomUUID());
    lab.setOwnerUserId(ownerUser);
    lab.setKind(SubjectKind.LAB);
    lab.setDefaultTimezone("Asia/Seoul");
    lab.setSettings(new HashMap<>());
    labId = subjectRepo.save(lab).getId();

    seedMember(ownerUser, SubjectRole.OWNER);
    seedMember(memberUser, SubjectRole.MEMBER);
  }

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  @Transactional
  void full_flow_code_join_pending_then_approve_inserts_member() {
    enterLabAs(ownerUser, SubjectRole.OWNER);
    String code = labJoinService.rotateInviteCode(ownerUser).getCode();
    assertThat(code).hasSize(8);

    // 신청자: SYSTEM scope(비멤버, 헤더 없음)
    SubjectContextHolder.set(SubjectContext.system());
    LabJoinService.JoinOutcome outcome = labJoinService.join(code, applicantUser);
    assertThat(outcome.status()).isEqualTo(LabJoinStatus.PENDING);
    // 멱등 — 재신청은 기존 pending 반환(부분 유니크 위반 없이)
    assertThat(labJoinService.join(code, applicantUser).status()).isEqualTo(LabJoinStatus.PENDING);

    // 관리자: 대기열 확인 → 승인
    enterLabAs(ownerUser, SubjectRole.OWNER);
    var pendingList = labJoinService.listPending();
    assertThat(pendingList).hasSize(1);
    assertThat(pendingList.get(0).userId()).isEqualTo(applicantUser);

    labJoinService.approve(pendingList.get(0).id(), ownerUser);

    SubjectMemberEntity joined =
        memberRepo.findById(new SubjectMemberId(labId, applicantUser)).orElseThrow();
    assertThat(joined.getRole()).isEqualTo(SubjectRole.MEMBER);
    assertThat(joined.getRemovedAt()).isNull();

    // 멤버가 된 뒤 재가입 시도 → 409
    SubjectContextHolder.set(SubjectContext.system());
    assertThatThrownBy(() -> labJoinService.join(code, applicantUser))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @Transactional
  void reject_keeps_user_out_and_rotation_invalidates_old_code() {
    enterLabAs(ownerUser, SubjectRole.OWNER);
    String oldCode = labJoinService.rotateInviteCode(ownerUser).getCode();

    SubjectContextHolder.set(SubjectContext.system());
    labJoinService.join(oldCode, applicantUser);

    enterLabAs(ownerUser, SubjectRole.OWNER);
    var pending = labJoinService.listPending();
    labJoinService.reject(pending.get(0).id(), ownerUser);
    assertThat(memberRepo.findById(new SubjectMemberId(labId, applicantUser))).isEmpty();
    assertThat(labJoinService.listPending()).isEmpty();

    // 회전 → 구 코드는 무효(404 동형)
    String newCode = labJoinService.rotateInviteCode(ownerUser).getCode();
    assertThat(newCode).isNotEqualTo(oldCode);
    SubjectContextHolder.set(SubjectContext.system());
    assertThatThrownBy(() -> labJoinService.join(oldCode, seedUser("late-applicant")))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Transactional
  void member_cannot_manage_join_surfaces_and_non_lab_subject_is_forbidden() {
    // 일반 MEMBER는 코드 발급·대기열·판정 불가
    enterLabAs(memberUser, SubjectRole.MEMBER);
    assertThatThrownBy(() -> labJoinService.rotateInviteCode(memberUser))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> labJoinService.listPending()).isInstanceOf(ForbiddenException.class);

    // personal 주제 컨텍스트(=isLab false)는 OWNER여도 가입 표면 차단
    SubjectContextHolder.set(
        SubjectContext.personal(labId, ownerUser, SubjectRole.OWNER, SubjectKind.PERSONAL));
    assertThatThrownBy(() -> labJoinService.rotateInviteCode(ownerUser))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("lab subject required");
  }

  private void enterLabAs(UUID userId, SubjectRole role) {
    SubjectContextHolder.set(SubjectContext.personal(labId, userId, role, SubjectKind.LAB));
  }

  private UUID seedUser(String prefix) {
    UserEntity user = new UserEntity();
    user.setEmail(prefix + "-" + UUID.randomUUID() + "@test.local");
    return userRepo.save(user).getId();
  }

  private void seedMember(UUID userId, SubjectRole role) {
    SubjectMemberEntity member = new SubjectMemberEntity();
    member.setId(new SubjectMemberId(labId, userId));
    member.setRole(role);
    memberRepo.save(member);
  }
}

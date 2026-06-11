package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectKind;
import com.laminar.context.SubjectRole;
import com.laminar.equipment.application.EquipmentReservationService;
import com.laminar.equipment.application.EquipmentService;
import com.laminar.equipment.domain.EquipmentEntity;
import com.laminar.equipment.domain.EquipmentReservationEntity;
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
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장비 LAB 스코프 회귀 (L3, LAB재설계 §3) — 크로스 lab 격리(목록·단건·예약), 역할 차등(CRUD=ADMIN+/예약=MEMBER+/취소=본인 or
 * ADMIN), personal 주제 차단.
 */
class EquipmentLabScopeIT extends IsolationIntegrationBase {

  @Autowired EquipmentService equipmentService;
  @Autowired EquipmentReservationService reservationService;
  @Autowired UserSystemRepository userRepo;
  @Autowired SubjectRepository subjectRepo;
  @Autowired SubjectMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID labA;
  private UUID labZ;
  private UUID ownerA;
  private UUID memberB;
  private UUID ownerZ;

  @BeforeEach
  void seed() {
    SubjectContextHolder.clear();
    ownerA = seedUser("eq-owner-a");
    memberB = seedUser("eq-member-b");
    ownerZ = seedUser("eq-owner-z");

    labA = seedLab("Lab A", ownerA);
    labZ = seedLab("Lab Z", ownerZ);
    seedMember(labA, ownerA, SubjectRole.OWNER);
    seedMember(labA, memberB, SubjectRole.MEMBER);
    seedMember(labZ, ownerZ, SubjectRole.OWNER);
  }

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  @Transactional
  void cross_lab_rows_are_invisible_and_unreachable() {
    enterLab(labA, ownerA, SubjectRole.OWNER);
    EquipmentEntity device = equipmentService.create("HPLC", null, "B1-101", null);

    // 다른 lab에서는 목록·단건·예약 전부 불가
    enterLab(labZ, ownerZ, SubjectRole.OWNER);
    assertThat(equipmentService.listActive()).isEmpty();
    assertThat(equipmentService.findById(device.getId())).isEmpty();
    assertThatThrownBy(
            () ->
                reservationService.reserve(
                    device.getId(),
                    OffsetDateTime.now().plusHours(1),
                    OffsetDateTime.now().plusHours(2),
                    "잠입 예약",
                    null,
                    null))
        .isInstanceOf(NotFoundException.class);

    // 자기 lab에서는 보인다
    enterLab(labA, memberB, SubjectRole.MEMBER);
    assertThat(equipmentService.listActive())
        .extracting(EquipmentEntity::getName)
        .containsExactly("HPLC");
  }

  @Test
  @Transactional
  void role_matrix_member_reserves_but_cannot_manage() {
    enterLab(labA, ownerA, SubjectRole.OWNER);
    EquipmentEntity device = equipmentService.create("Microscope", null, null, null);

    // MEMBER: 장비 CRUD 불가, 예약 가능
    enterLab(labA, memberB, SubjectRole.MEMBER);
    assertThatThrownBy(() -> equipmentService.create("불법 장비", null, null, null))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("lab admin required");
    EquipmentReservationEntity mine =
        reservationService.reserve(
            device.getId(),
            OffsetDateTime.now().plusHours(1),
            OffsetDateTime.now().plusHours(2),
            "측정",
            null,
            null);
    assertThat(mine.getSubjectId()).isEqualTo(labA);

    // MEMBER 본인 취소 가능
    reservationService.cancel(mine.getId());

    // 타인 예약은 MEMBER가 못 지운다 — OWNER(ADMIN+)는 가능
    enterLab(labA, ownerA, SubjectRole.OWNER);
    EquipmentReservationEntity owners =
        reservationService.reserve(
            device.getId(),
            OffsetDateTime.now().plusHours(3),
            OffsetDateTime.now().plusHours(4),
            "유지보수",
            null,
            null);
    enterLab(labA, memberB, SubjectRole.MEMBER);
    assertThatThrownBy(() -> reservationService.cancel(owners.getId()))
        .isInstanceOf(ForbiddenException.class);
    enterLab(labA, ownerA, SubjectRole.OWNER);
    reservationService.cancel(owners.getId()); // ADMIN override

    // ADMIN 역할은 장비 CRUD 가능
    enterLab(labA, memberB, SubjectRole.ADMIN);
    EquipmentEntity byAdmin = equipmentService.create("Centrifuge", null, null, null);
    assertThat(byAdmin.getSubjectId()).isEqualTo(labA);
  }

  @Test
  @Transactional
  void personal_subject_has_no_equipment_surface() {
    enterPersonal(labA, ownerA, SubjectRole.OWNER);
    assertThatThrownBy(() -> equipmentService.listActive())
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("lab subject required");
    assertThatThrownBy(() -> equipmentService.create("X", null, null, null))
        .isInstanceOf(ForbiddenException.class);
  }

  private void enterLab(UUID subjectId, UUID userId, SubjectRole role) {
    SubjectContextHolder.set(SubjectContext.personal(subjectId, userId, role, SubjectKind.LAB));
    filterActivator.activate();
  }

  private void enterPersonal(UUID subjectId, UUID userId, SubjectRole role) {
    SubjectContextHolder.set(
        SubjectContext.personal(subjectId, userId, role, SubjectKind.PERSONAL));
    filterActivator.activate();
  }

  private UUID seedLab(String name, UUID ownerUserId) {
    SubjectEntity lab = new SubjectEntity();
    lab.setName(name);
    lab.setSlug("eq-lab-" + UUID.randomUUID());
    lab.setOwnerUserId(ownerUserId);
    lab.setKind(SubjectKind.LAB);
    lab.setDefaultTimezone("Asia/Seoul");
    lab.setSettings(new HashMap<>());
    return subjectRepo.save(lab).getId();
  }

  private UUID seedUser(String prefix) {
    UserEntity user = new UserEntity();
    user.setEmail(prefix + "-" + UUID.randomUUID() + "@test.local");
    return userRepo.save(user).getId();
  }

  private void seedMember(UUID subjectId, UUID userId, SubjectRole role) {
    SubjectMemberEntity member = new SubjectMemberEntity();
    member.setId(new SubjectMemberId(subjectId, userId));
    member.setRole(role);
    memberRepo.save(member);
  }
}

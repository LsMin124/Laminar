package com.laminar.equipment.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.equipment.domain.EquipmentEntity;
import com.laminar.equipment.repository.EquipmentRepository;
import com.laminar.error.ConflictException;
import com.laminar.error.NotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공용 자원 (장비) CRUD — LAB(주제 kind=lab) 스코프 (L3, LAB재설계 §3).
 *
 * <p>§1.3 매트릭스: 조회는 lab 멤버 전원, 등록/수정/활성/삭제는 ADMIN+. personal 주제에서는 장비 표면이 존재하지
 * 않는다(requireLabMember가 403). 구 사용자(owner) 스코프의 ownsUser 검증은 ownsShared(lab)로 대체.
 */
@Service
public class EquipmentService {

  private final EquipmentRepository equipmentRepo;

  public EquipmentService(EquipmentRepository equipmentRepo) {
    this.equipmentRepo = equipmentRepo;
  }

  @Transactional
  public EquipmentEntity create(
      String name,
      String description,
      String location,
      List<Map<String, Object>> defaultLogColumns) {
    SubjectContext ctx = SubjectContextHolder.requireLabAdmin("equipment");
    if (equipmentRepo.findByNameAndDeletedAtIsNull(name).isPresent()) {
      throw new ConflictException("equipment name already exists: " + name);
    }

    EquipmentEntity equipment = new EquipmentEntity();
    equipment.setSubjectId(ctx.subjectId());
    equipment.setCreatedBy(ctx.userId());
    equipment.setName(name);
    equipment.setDescription(description);
    equipment.setLocation(location);
    equipment.setActive(true);
    equipment.setDefaultLogColumns(
        defaultLogColumns == null ? new ArrayList<>() : defaultLogColumns);
    return equipmentRepo.save(equipment);
  }

  @Transactional(readOnly = true)
  public List<EquipmentEntity> listActive() {
    SubjectContextHolder.requireLabMember("equipment");
    return equipmentRepo.findByActiveTrueAndDeletedAtIsNullOrderByName();
  }

  @Transactional(readOnly = true)
  public List<EquipmentEntity> listAll() {
    SubjectContextHolder.requireLabMember("equipment");
    return equipmentRepo.findByDeletedAtIsNullOrderByName();
  }

  @Transactional(readOnly = true)
  public Optional<EquipmentEntity> findById(UUID equipmentId) {
    SubjectContext ctx = SubjectContextHolder.requireLabMember("equipment");
    return equipmentRepo
        .findById(equipmentId)
        .filter(e -> e.getDeletedAt() == null)
        // PK 로드는 @Filter 비적용 — 명시 lab 소유 검증 (fail-closed)
        .filter(e -> ctx.ownsShared(e.getSubjectId()));
  }

  @Transactional
  public EquipmentEntity update(
      UUID equipmentId,
      String name,
      String description,
      String location,
      List<Map<String, Object>> defaultLogColumns) {
    SubjectContext ctx = SubjectContextHolder.requireLabAdmin("equipment");
    EquipmentEntity equipment =
        equipmentRepo
            .findById(equipmentId)
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> ctx.ownsShared(e.getSubjectId()))
            .orElseThrow(() -> new NotFoundException("equipment not found"));
    if (name != null && !name.isBlank()) equipment.setName(name);
    if (description != null) equipment.setDescription(description);
    if (location != null) equipment.setLocation(location);
    if (defaultLogColumns != null) equipment.setDefaultLogColumns(defaultLogColumns);
    return equipmentRepo.save(equipment);
  }

  @Transactional
  public EquipmentEntity toggleActive(UUID equipmentId, boolean active) {
    SubjectContext ctx = SubjectContextHolder.requireLabAdmin("equipment");
    EquipmentEntity equipment =
        equipmentRepo
            .findById(equipmentId)
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> ctx.ownsShared(e.getSubjectId()))
            .orElseThrow(() -> new NotFoundException("equipment not found"));
    equipment.setActive(active);
    return equipmentRepo.save(equipment);
  }

  @Transactional
  public void softDelete(UUID equipmentId) {
    SubjectContext ctx = SubjectContextHolder.requireLabAdmin("equipment");
    equipmentRepo
        .findById(equipmentId)
        .filter(e -> e.getDeletedAt() == null)
        .filter(e -> ctx.ownsShared(e.getSubjectId()))
        .ifPresent(
            e -> {
              e.setDeletedAt(OffsetDateTime.now());
              equipmentRepo.save(e);
            });
  }
}

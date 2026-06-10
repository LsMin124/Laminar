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
 * 공용 자원 (장비) CRUD — subject-shared.
 *
 * <p>모든 워크스페이스 멤버 read·write, OWNER만 일부 수정 (활성화 토글 등 정책 — 필요 시 service에서 강제).
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
    SubjectContext ctx = requireSubjectWritable();
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
    SubjectContextHolder.require();
    return equipmentRepo.findByActiveTrueAndDeletedAtIsNullOrderByName();
  }

  @Transactional(readOnly = true)
  public List<EquipmentEntity> listAll() {
    SubjectContextHolder.require();
    return equipmentRepo.findByDeletedAtIsNullOrderByName();
  }

  @Transactional(readOnly = true)
  public Optional<EquipmentEntity> findById(UUID equipmentId) {
    SubjectContext ctx = SubjectContextHolder.require();
    return equipmentRepo
        .findById(equipmentId)
        .filter(e -> e.getDeletedAt() == null)
        .filter(e -> ctx.ownsUser(e.getCreatedBy()));
  }

  @Transactional
  public EquipmentEntity update(
      UUID equipmentId,
      String name,
      String description,
      String location,
      List<Map<String, Object>> defaultLogColumns) {
    SubjectContext ctx = requireSubjectWritable();
    EquipmentEntity equipment =
        equipmentRepo
            .findById(equipmentId)
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> ctx.ownsUser(e.getCreatedBy()))
            .orElseThrow(() -> new NotFoundException("equipment not found"));
    if (name != null && !name.isBlank()) equipment.setName(name);
    if (description != null) equipment.setDescription(description);
    if (location != null) equipment.setLocation(location);
    if (defaultLogColumns != null) equipment.setDefaultLogColumns(defaultLogColumns);
    return equipmentRepo.save(equipment);
  }

  @Transactional
  public EquipmentEntity toggleActive(UUID equipmentId, boolean active) {
    SubjectContext ctx = requireSubjectWritable();
    EquipmentEntity equipment =
        equipmentRepo
            .findById(equipmentId)
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> ctx.ownsUser(e.getCreatedBy()))
            .orElseThrow(() -> new NotFoundException("equipment not found"));
    equipment.setActive(active);
    return equipmentRepo.save(equipment);
  }

  @Transactional
  public void softDelete(UUID equipmentId) {
    SubjectContext ctx = requireOwner();
    equipmentRepo
        .findById(equipmentId)
        .filter(e -> e.getDeletedAt() == null)
        .filter(e -> ctx.ownsUser(e.getCreatedBy()))
        .ifPresent(
            e -> {
              e.setDeletedAt(OffsetDateTime.now());
              equipmentRepo.save(e);
            });
  }

  private SubjectContext requireSubjectWritable() {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.subjectId() == null) {
      throw new IllegalStateException("subject scope required");
    }
    if (ctx.scope() == SubjectContext.Scope.PERSONAL && !ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate equipment");
    }
    return ctx;
  }

  private SubjectContext requireOwner() {
    SubjectContext ctx = requireSubjectWritable();
    if (!ctx.isOwner()) {
      throw new IllegalStateException("OWNER role required for equipment delete");
    }
    return ctx;
  }
}

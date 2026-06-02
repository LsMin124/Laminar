package com.laminar.equipment;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장비 담당자 N:N (Spec Q12).
 *
 * <p>OWNER만 임명·해임 가능. 담당자는 해당 장비 정책 (점검, 사용 승인 등)을 추가로 수정 가능 — 권한 검사는 호출 측.
 */
@Service
public class EquipmentAdminService {

  private final EquipmentAdminRepository adminRepo;
  private final EquipmentRepository equipmentRepo;

  public EquipmentAdminService(
      EquipmentAdminRepository adminRepo, EquipmentRepository equipmentRepo) {
    this.adminRepo = adminRepo;
    this.equipmentRepo = equipmentRepo;
  }

  @Transactional
  public EquipmentAdminEntity appoint(UUID equipmentId, UUID userId) {
    WorkspaceContext ctx = requireOwner();
    equipmentRepo
        .findById(equipmentId)
        .filter(e -> e.getDeletedAt() == null)
        .filter(e -> ctx.ownsShared(e.getWorkspaceId()))
        .orElseThrow(() -> new IllegalArgumentException("equipment not found"));

    EquipmentAdminEntity admin = new EquipmentAdminEntity();
    admin.setId(new EquipmentAdminId(equipmentId, userId));
    admin.setAppointedBy(ctx.userId());
    return adminRepo.save(admin);
  }

  @Transactional
  public void dismiss(UUID equipmentId, UUID userId) {
    WorkspaceContext ctx = requireOwner();
    // 장비 소유권(workspace) 선검증 — 타 workspace 장비 담당자 해임 차단
    equipmentRepo
        .findById(equipmentId)
        .filter(e -> e.getDeletedAt() == null)
        .filter(e -> ctx.ownsShared(e.getWorkspaceId()))
        .orElseThrow(() -> new IllegalArgumentException("equipment not found"));
    adminRepo.findById(new EquipmentAdminId(equipmentId, userId)).ifPresent(adminRepo::delete);
  }

  @Transactional(readOnly = true)
  public List<UUID> listAdminUserIds(UUID equipmentId) {
    WorkspaceContextHolder.requirePersonal();
    return adminRepo.findByIdEquipmentId(equipmentId).stream()
        .map(a -> a.getId().getUserId())
        .toList();
  }

  @Transactional(readOnly = true)
  public List<UUID> listEquipmentIdsForUser(UUID userId) {
    WorkspaceContextHolder.requirePersonal();
    return adminRepo.findByIdUserId(userId).stream().map(a -> a.getId().getEquipmentId()).toList();
  }

  @Transactional(readOnly = true)
  public boolean isAdmin(UUID equipmentId, UUID userId) {
    WorkspaceContextHolder.requirePersonal();
    return adminRepo.findById(new EquipmentAdminId(equipmentId, userId)).isPresent();
  }

  private WorkspaceContext requireOwner() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.workspaceId() == null) {
      throw new IllegalStateException("workspace scope required");
    }
    if (ctx.scope() == WorkspaceContext.Scope.PERSONAL && !ctx.isOwner()) {
      throw new IllegalStateException("OWNER role required to appoint equipment admin");
    }
    return ctx;
  }
}

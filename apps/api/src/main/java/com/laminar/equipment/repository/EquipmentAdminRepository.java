package com.laminar.equipment.repository;

import com.laminar.equipment.domain.EquipmentAdminEntity;
import com.laminar.equipment.domain.EquipmentAdminId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 장비 담당자 junction — parent (equipment) 격리에 의존 (workspace_id 컬럼 없음). */
public interface EquipmentAdminRepository
    extends JpaRepository<EquipmentAdminEntity, EquipmentAdminId> {

  List<EquipmentAdminEntity> findByIdEquipmentId(UUID equipmentId);

  List<EquipmentAdminEntity> findByIdUserId(UUID userId);
}

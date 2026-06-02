package com.laminar.equipment.repository;

import com.laminar.equipment.domain.EquipmentLogEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentLogRepository extends JpaRepository<EquipmentLogEntity, UUID> {

  List<EquipmentLogEntity> findByEquipmentIdAndDeletedAtIsNullOrderByLoggedAtDesc(UUID equipmentId);

  List<EquipmentLogEntity> findByEquipmentIdAndLoggedAtBetweenAndDeletedAtIsNull(
      UUID equipmentId, OffsetDateTime from, OffsetDateTime to);
}

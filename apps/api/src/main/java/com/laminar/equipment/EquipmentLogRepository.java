package com.laminar.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EquipmentLogRepository extends JpaRepository<EquipmentLogEntity, UUID> {

    List<EquipmentLogEntity> findByEquipmentIdAndDeletedAtIsNullOrderByLoggedAtDesc(UUID equipmentId);

    List<EquipmentLogEntity> findByEquipmentIdAndLoggedAtBetweenAndDeletedAtIsNull(
            UUID equipmentId, OffsetDateTime from, OffsetDateTime to);
}

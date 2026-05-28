package com.laminar.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentLogColumnRepository extends JpaRepository<EquipmentLogColumnEntity, UUID> {

    List<EquipmentLogColumnEntity> findByEquipmentIdAndDeletedAtIsNullOrderByPriorityAsc(UUID equipmentId);

    Optional<EquipmentLogColumnEntity> findFirstByEquipmentIdAndDeletedAtIsNullOrderByPriorityDesc(UUID equipmentId);

    Optional<EquipmentLogColumnEntity> findByEquipmentIdAndColumnKeyAndDeletedAtIsNull(
            UUID equipmentId, String columnKey);
}

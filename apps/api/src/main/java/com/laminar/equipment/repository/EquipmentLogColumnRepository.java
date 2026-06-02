package com.laminar.equipment.repository;

import com.laminar.equipment.domain.EquipmentLogColumnEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentLogColumnRepository
    extends JpaRepository<EquipmentLogColumnEntity, UUID> {

  List<EquipmentLogColumnEntity> findByEquipmentIdAndDeletedAtIsNullOrderByPriorityAsc(
      UUID equipmentId);

  Optional<EquipmentLogColumnEntity> findFirstByEquipmentIdAndDeletedAtIsNullOrderByPriorityDesc(
      UUID equipmentId);

  Optional<EquipmentLogColumnEntity> findByEquipmentIdAndColumnKeyAndDeletedAtIsNull(
      UUID equipmentId, String columnKey);
}

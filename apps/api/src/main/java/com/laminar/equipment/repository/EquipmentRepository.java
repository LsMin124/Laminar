package com.laminar.equipment.repository;

import com.laminar.equipment.domain.EquipmentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 장비 Repository — subject-shared (@Filter 자동). */
public interface EquipmentRepository extends JpaRepository<EquipmentEntity, UUID> {

  List<EquipmentEntity> findByDeletedAtIsNullOrderByName();

  List<EquipmentEntity> findByActiveTrueAndDeletedAtIsNullOrderByName();

  Optional<EquipmentEntity> findByNameAndDeletedAtIsNull(String name);
}

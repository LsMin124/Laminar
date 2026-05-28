package com.laminar.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 장비 Repository — workspace-shared (@Filter 자동).
 */
public interface EquipmentRepository extends JpaRepository<EquipmentEntity, UUID> {

    List<EquipmentEntity> findByDeletedAtIsNullOrderByName();

    List<EquipmentEntity> findByActiveTrueAndDeletedAtIsNullOrderByName();

    Optional<EquipmentEntity> findByNameAndDeletedAtIsNull(String name);
}

package com.laminar.equipment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedCalendarRepository extends JpaRepository<SharedCalendarEntity, UUID> {

  List<SharedCalendarEntity> findByDeletedAtIsNullOrderByName();

  Optional<SharedCalendarEntity> findByEquipmentIdAndDeletedAtIsNull(UUID equipmentId);
}

package com.laminar.equipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EquipmentAdminId implements Serializable {

  @Column(name = "equipment_id", nullable = false)
  private UUID equipmentId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  public EquipmentAdminId() {}

  public EquipmentAdminId(UUID equipmentId, UUID userId) {
    this.equipmentId = equipmentId;
    this.userId = userId;
  }

  public UUID getEquipmentId() {
    return equipmentId;
  }

  public void setEquipmentId(UUID equipmentId) {
    this.equipmentId = equipmentId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EquipmentAdminId that)) return false;
    return Objects.equals(equipmentId, that.equipmentId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(equipmentId, userId);
  }
}

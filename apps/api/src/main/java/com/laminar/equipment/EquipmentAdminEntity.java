package com.laminar.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "equipment_admins")
public class EquipmentAdminEntity {

    @EmbeddedId
    private EquipmentAdminId id;

    @Column(name = "appointed_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime appointedAt;

    @Column(name = "appointed_by")
    private UUID appointedBy;

    public EquipmentAdminId getId() { return id; }
    public void setId(EquipmentAdminId id) { this.id = id; }

    public OffsetDateTime getAppointedAt() { return appointedAt; }

    public UUID getAppointedBy() { return appointedBy; }
    public void setAppointedBy(UUID appointedBy) { this.appointedBy = appointedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipmentAdminEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}

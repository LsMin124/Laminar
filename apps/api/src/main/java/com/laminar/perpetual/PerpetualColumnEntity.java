package com.laminar.perpetual;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "perpetual_columns")
@Getter
@Setter
public class PerpetualColumnEntity {

  @EmbeddedId private PerpetualColumnId id;

  @Column(name = "value")
  private String value;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PerpetualColumnEntity that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : getClass().hashCode();
  }
}

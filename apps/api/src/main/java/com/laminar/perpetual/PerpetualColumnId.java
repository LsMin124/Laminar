package com.laminar.perpetual;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class PerpetualColumnId implements Serializable {

  @Column(name = "perpetual_note_id", nullable = false)
  private UUID perpetualNoteId;

  @Column(name = "column_definition_id", nullable = false)
  private UUID columnDefinitionId;

  public PerpetualColumnId() {}

  public PerpetualColumnId(UUID perpetualNoteId, UUID columnDefinitionId) {
    this.perpetualNoteId = perpetualNoteId;
    this.columnDefinitionId = columnDefinitionId;
  }

  public UUID getPerpetualNoteId() {
    return perpetualNoteId;
  }

  public void setPerpetualNoteId(UUID perpetualNoteId) {
    this.perpetualNoteId = perpetualNoteId;
  }

  public UUID getColumnDefinitionId() {
    return columnDefinitionId;
  }

  public void setColumnDefinitionId(UUID columnDefinitionId) {
    this.columnDefinitionId = columnDefinitionId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PerpetualColumnId that)) return false;
    return Objects.equals(perpetualNoteId, that.perpetualNoteId)
        && Objects.equals(columnDefinitionId, that.columnDefinitionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(perpetualNoteId, columnDefinitionId);
  }
}

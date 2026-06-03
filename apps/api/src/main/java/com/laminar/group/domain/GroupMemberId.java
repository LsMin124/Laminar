package com.laminar.group.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class GroupMemberId implements Serializable {

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(name = "card_id", nullable = false)
  private UUID cardId;

  public GroupMemberId() {}

  public GroupMemberId(UUID groupId, UUID cardId) {
    this.groupId = groupId;
    this.cardId = cardId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public void setGroupId(UUID groupId) {
    this.groupId = groupId;
  }

  public UUID getCardId() {
    return cardId;
  }

  public void setCardId(UUID cardId) {
    this.cardId = cardId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GroupMemberId that)) return false;
    return Objects.equals(groupId, that.groupId) && Objects.equals(cardId, that.cardId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, cardId);
  }
}

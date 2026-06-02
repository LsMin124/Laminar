package com.laminar.tab;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class TabMemberId implements Serializable {

  @Column(name = "tab_id", nullable = false)
  private UUID tabId;

  @Column(name = "card_id", nullable = false)
  private UUID cardId;

  public TabMemberId() {}

  public TabMemberId(UUID tabId, UUID cardId) {
    this.tabId = tabId;
    this.cardId = cardId;
  }

  public UUID getTabId() {
    return tabId;
  }

  public void setTabId(UUID tabId) {
    this.tabId = tabId;
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
    if (!(o instanceof TabMemberId that)) return false;
    return Objects.equals(tabId, that.tabId) && Objects.equals(cardId, that.cardId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tabId, cardId);
  }
}

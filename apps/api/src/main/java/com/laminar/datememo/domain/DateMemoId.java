package com.laminar.datememo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DateMemoId implements Serializable {

  @Column(name = "tab_id", nullable = false)
  private UUID tabId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  public DateMemoId() {}

  public DateMemoId(UUID tabId, UUID userId, LocalDate date) {
    this.tabId = tabId;
    this.userId = userId;
    this.date = date;
  }

  public UUID getTabId() {
    return tabId;
  }

  public void setTabId(UUID tabId) {
    this.tabId = tabId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DateMemoId that)) return false;
    return Objects.equals(tabId, that.tabId)
        && Objects.equals(userId, that.userId)
        && Objects.equals(date, that.date);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tabId, userId, date);
  }
}

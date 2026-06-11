package com.laminar.subject.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/** LAB 가입 신청 상태 — pending(대기) → approved(승인, 멤버 INSERT) | rejected(거절). 재신청 가능(이력 보존). */
public enum LabJoinStatus {
  PENDING("pending"),
  APPROVED("approved"),
  REJECTED("rejected");

  private final String dbValue;

  LabJoinStatus(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static LabJoinStatus fromDbValue(String dbValue) {
    return Arrays.stream(values())
        .filter(s -> s.dbValue.equals(dbValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown join status: " + dbValue));
  }

  @Converter(autoApply = true)
  public static class LabJoinStatusConverter implements AttributeConverter<LabJoinStatus, String> {

    @Override
    public LabJoinStatus convertToEntityAttribute(String dbData) {
      return dbData == null ? null : fromDbValue(dbData);
    }

    @Override
    public String convertToDatabaseColumn(LabJoinStatus attribute) {
      return attribute == null ? null : attribute.dbValue;
    }
  }
}

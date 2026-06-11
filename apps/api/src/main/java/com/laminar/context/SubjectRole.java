package com.laminar.context;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/**
 * 멤버 역할 3등급 — 소유자/관리자/일반멤버 (2026-06-11 제품 결정, LAB재설계 §1.2·§1.3 매트릭스).
 *
 * <p>구 VIEWER(읽기 전용)는 V29에서 퇴역(기존 행은 member로 흡수) — 읽기 전용 등급이 다시 필요해지면 그때 추가.
 */
public enum SubjectRole {
  OWNER("owner"),
  ADMIN("admin"),
  MEMBER("member");

  private final String dbValue;

  SubjectRole(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static SubjectRole fromDbValue(String dbValue) {
    return Arrays.stream(values())
        .filter(r -> r.dbValue.equals(dbValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown subject role: " + dbValue));
  }

  @Converter(autoApply = true)
  public static class SubjectRoleConverter implements AttributeConverter<SubjectRole, String> {

    @Override
    public String convertToDatabaseColumn(SubjectRole attribute) {
      return attribute == null ? null : attribute.dbValue;
    }

    @Override
    public SubjectRole convertToEntityAttribute(String dbData) {
      return dbData == null ? null : fromDbValue(dbData);
    }
  }
}

package com.laminar.context;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/**
 * 주제 종별 — personal(기본) | lab(승격). 격리 모델 어휘라 {@link SubjectRole} 선례대로 context 소속 (DX-16).
 *
 * <p>LAB = 연구실 단위 공유 주제(LAB재설계 §1.1): 장비·가입 흐름 등 lab 전용 표면이 {@code SubjectContext.isLab()}으로 판정한다.
 * 승격은 OWNER 전용, 강등 미지원.
 */
public enum SubjectKind {
  PERSONAL("personal"),
  LAB("lab");

  private final String dbValue;

  SubjectKind(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static SubjectKind fromDbValue(String dbValue) {
    return Arrays.stream(values())
        .filter(k -> k.dbValue.equals(dbValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown subject kind: " + dbValue));
  }

  @Converter(autoApply = true)
  public static class SubjectKindConverter implements AttributeConverter<SubjectKind, String> {

    @Override
    public String convertToDatabaseColumn(SubjectKind attribute) {
      return attribute == null ? null : attribute.dbValue;
    }

    @Override
    public SubjectKind convertToEntityAttribute(String dbData) {
      return dbData == null ? null : fromDbValue(dbData);
    }
  }
}

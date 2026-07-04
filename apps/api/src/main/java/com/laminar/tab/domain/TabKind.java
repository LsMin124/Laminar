package com.laminar.tab.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/**
 * 탭 종류 — DAG 캔버스(카드=시간축 강제) vs 화이트보드(자유 배치 노드 + 관계 화살표).
 *
 * <p>진입 분기의 정본(사용자 확정 결정): 탭 생성 시 종류를 고르며, 화이트보드 탭도 여러 개 가능. 기존 탭은 전부 {@link #DAG}(V33 default
 * 'dag').
 */
public enum TabKind {
  DAG("dag"),
  WHITEBOARD("whiteboard");

  private final String dbValue;

  TabKind(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static TabKind fromDbValue(String dbValue) {
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(dbValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown tab kind: " + dbValue));
  }

  @Converter(autoApply = true)
  public static class TabKindConverter implements AttributeConverter<TabKind, String> {

    @Override
    public String convertToDatabaseColumn(TabKind attribute) {
      return attribute == null ? null : attribute.dbValue;
    }

    @Override
    public TabKind convertToEntityAttribute(String dbData) {
      return dbData == null ? null : fromDbValue(dbData);
    }
  }
}

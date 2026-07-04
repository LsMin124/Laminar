package com.laminar.whiteboard.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/**
 * 화이트보드 노드 종류 — v1 범위(사용자 확정 결정): 마크다운 문서 · 이미지. PDF·영상은 v2(CSP media/frame-src·인라인 presign·뷰어가 더 큰
 * 작업이라 분리).
 */
public enum WhiteboardNodeKind {
  MD("md"),
  IMAGE("image");

  private final String dbValue;

  WhiteboardNodeKind(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static WhiteboardNodeKind fromDbValue(String dbValue) {
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(dbValue))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown whiteboard node kind: " + dbValue));
  }

  @Converter(autoApply = true)
  public static class WhiteboardNodeKindConverter
      implements AttributeConverter<WhiteboardNodeKind, String> {

    @Override
    public String convertToDatabaseColumn(WhiteboardNodeKind attribute) {
      return attribute == null ? null : attribute.dbValue;
    }

    @Override
    public WhiteboardNodeKind convertToEntityAttribute(String dbData) {
      return dbData == null ? null : fromDbValue(dbData);
    }
  }
}

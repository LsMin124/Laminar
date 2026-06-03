package com.laminar.board.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

public enum BoardDefaultView {
  CALENDAR("calendar"),
  LIST("list");

  private final String dbValue;

  BoardDefaultView(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static BoardDefaultView fromDbValue(String dbValue) {
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(dbValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown board default_view: " + dbValue));
  }

  @Converter(autoApply = true)
  public static class BoardDefaultViewConverter
      implements AttributeConverter<BoardDefaultView, String> {

    @Override
    public String convertToDatabaseColumn(BoardDefaultView attribute) {
      return attribute == null ? null : attribute.dbValue;
    }

    @Override
    public BoardDefaultView convertToEntityAttribute(String dbData) {
      return dbData == null ? null : fromDbValue(dbData);
    }
  }
}

package com.laminar.tab.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

public enum TabDefaultView {
  CALENDAR("calendar"),
  LIST("list");

  private final String dbValue;

  TabDefaultView(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static TabDefaultView fromDbValue(String dbValue) {
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(dbValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown tab default_view: " + dbValue));
  }

  @Converter(autoApply = true)
  public static class TabDefaultViewConverter
      implements AttributeConverter<TabDefaultView, String> {

    @Override
    public String convertToDatabaseColumn(TabDefaultView attribute) {
      return attribute == null ? null : attribute.dbValue;
    }

    @Override
    public TabDefaultView convertToEntityAttribute(String dbData) {
      return dbData == null ? null : fromDbValue(dbData);
    }
  }
}

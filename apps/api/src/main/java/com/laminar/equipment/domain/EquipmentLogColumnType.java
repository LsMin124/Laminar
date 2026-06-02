package com.laminar.equipment.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

public enum EquipmentLogColumnType {
  TEXT("text"),
  NUMBER("number"),
  ENUM("enum"),
  BOOL("bool"),
  DATETIME("datetime");

  private final String dbValue;

  EquipmentLogColumnType(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static EquipmentLogColumnType fromDbValue(String dbValue) {
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(dbValue))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown equipment log column type: " + dbValue));
  }

  @Converter(autoApply = true)
  public static class EquipmentLogColumnTypeConverter
      implements AttributeConverter<EquipmentLogColumnType, String> {

    @Override
    public String convertToDatabaseColumn(EquipmentLogColumnType attribute) {
      return attribute == null ? null : attribute.dbValue;
    }

    @Override
    public EquipmentLogColumnType convertToEntityAttribute(String dbData) {
      return dbData == null ? null : fromDbValue(dbData);
    }
  }
}

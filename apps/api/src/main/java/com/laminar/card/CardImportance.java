package com.laminar.card;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

public enum CardImportance {
  NORMAL("normal"),
  CF("cf"),
  URGENT("urgent"),
  PURCHASE("purchase"),
  PERPETUAL_VER("perpetual-ver"),
  ARTICLE("article"),
  PROCESS("process");

  private final String dbValue;

  CardImportance(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static CardImportance fromDbValue(String dbValue) {
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(dbValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown card importance: " + dbValue));
  }

  @Converter(autoApply = true)
  public static class CardImportanceConverter
      implements AttributeConverter<CardImportance, String> {

    @Override
    public String convertToDatabaseColumn(CardImportance attribute) {
      return attribute == null ? null : attribute.dbValue;
    }

    @Override
    public CardImportance convertToEntityAttribute(String dbData) {
      return dbData == null ? null : fromDbValue(dbData);
    }
  }
}

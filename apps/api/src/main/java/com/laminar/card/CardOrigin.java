package com.laminar.card;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

public enum CardOrigin {
  MANUAL("manual"),
  RRULE_EXPANSION("rrule_expansion"),
  GCAL_PULL("gcal_pull"),
  EQUIPMENT_RESERVATION("equipment_reservation");

  private final String dbValue;

  CardOrigin(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static CardOrigin fromDbValue(String dbValue) {
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(dbValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown card origin: " + dbValue));
  }

  @Converter(autoApply = true)
  public static class CardOriginConverter implements AttributeConverter<CardOrigin, String> {

    @Override
    public String convertToDatabaseColumn(CardOrigin attribute) {
      return attribute == null ? null : attribute.dbValue;
    }

    @Override
    public CardOrigin convertToEntityAttribute(String dbData) {
      return dbData == null ? null : fromDbValue(dbData);
    }
  }
}

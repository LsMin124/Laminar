package com.laminar.attachment.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

public enum AttachmentParentType {
  CARD("card"),
  WHITEBOARD_NODE("whiteboard_node");

  private final String dbValue;

  AttachmentParentType(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static AttachmentParentType fromDbValue(String dbValue) {
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(dbValue))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown attachment parent type: " + dbValue));
  }

  @Converter(autoApply = true)
  public static class AttachmentParentTypeConverter
      implements AttributeConverter<AttachmentParentType, String> {

    @Override
    public String convertToDatabaseColumn(AttachmentParentType attribute) {
      return attribute == null ? null : attribute.dbValue;
    }

    @Override
    public AttachmentParentType convertToEntityAttribute(String dbData) {
      return dbData == null ? null : fromDbValue(dbData);
    }
  }
}

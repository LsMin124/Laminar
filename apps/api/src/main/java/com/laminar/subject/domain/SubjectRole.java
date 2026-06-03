package com.laminar.subject.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

public enum SubjectRole {
  OWNER("owner"),
  MEMBER("member"),
  VIEWER("viewer");

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

package com.laminar.perpetual;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

public enum PerpetualColumnType {
    TEXT("text"),
    DROPDOWN("dropdown"),
    CHECKBOX("checkbox");

    private final String dbValue;

    PerpetualColumnType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static PerpetualColumnType fromDbValue(String dbValue) {
        return Arrays.stream(values())
                .filter(v -> v.dbValue.equals(dbValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown perpetual column type: " + dbValue));
    }

    @Converter(autoApply = true)
    public static class PerpetualColumnTypeConverter
            implements AttributeConverter<PerpetualColumnType, String> {

        @Override
        public String convertToDatabaseColumn(PerpetualColumnType attribute) {
            return attribute == null ? null : attribute.dbValue;
        }

        @Override
        public PerpetualColumnType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : fromDbValue(dbData);
        }
    }
}

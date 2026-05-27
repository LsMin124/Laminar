package com.laminar.gcal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

public enum SyncDirection {
    PUSH("push"),
    PULL("pull"),
    TWO_WAY("two-way");

    private final String dbValue;

    SyncDirection(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static SyncDirection fromDbValue(String dbValue) {
        return Arrays.stream(values())
                .filter(v -> v.dbValue.equals(dbValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown sync direction: " + dbValue));
    }

    @Converter(autoApply = true)
    public static class SyncDirectionConverter
            implements AttributeConverter<SyncDirection, String> {

        @Override
        public String convertToDatabaseColumn(SyncDirection attribute) {
            return attribute == null ? null : attribute.dbValue;
        }

        @Override
        public SyncDirection convertToEntityAttribute(String dbData) {
            return dbData == null ? null : fromDbValue(dbData);
        }
    }
}

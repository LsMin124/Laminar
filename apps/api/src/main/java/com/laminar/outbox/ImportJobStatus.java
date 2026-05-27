package com.laminar.outbox;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

public enum ImportJobStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String dbValue;

    ImportJobStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static ImportJobStatus fromDbValue(String dbValue) {
        return Arrays.stream(values())
                .filter(v -> v.dbValue.equals(dbValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown import job status: " + dbValue));
    }

    @Converter(autoApply = true)
    public static class ImportJobStatusConverter
            implements AttributeConverter<ImportJobStatus, String> {

        @Override
        public String convertToDatabaseColumn(ImportJobStatus attribute) {
            return attribute == null ? null : attribute.dbValue;
        }

        @Override
        public ImportJobStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : fromDbValue(dbData);
        }
    }
}

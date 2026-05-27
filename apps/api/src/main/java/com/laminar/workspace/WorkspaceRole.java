package com.laminar.workspace;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

public enum WorkspaceRole {
    OWNER("owner"),
    MEMBER("member"),
    VIEWER("viewer");

    private final String dbValue;

    WorkspaceRole(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static WorkspaceRole fromDbValue(String dbValue) {
        return Arrays.stream(values())
                .filter(r -> r.dbValue.equals(dbValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown workspace role: " + dbValue));
    }

    @Converter(autoApply = true)
    public static class WorkspaceRoleConverter
            implements AttributeConverter<WorkspaceRole, String> {

        @Override
        public String convertToDatabaseColumn(WorkspaceRole attribute) {
            return attribute == null ? null : attribute.dbValue;
        }

        @Override
        public WorkspaceRole convertToEntityAttribute(String dbData) {
            return dbData == null ? null : fromDbValue(dbData);
        }
    }
}

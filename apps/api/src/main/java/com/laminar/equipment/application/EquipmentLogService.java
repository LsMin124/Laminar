package com.laminar.equipment.application;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.equipment.domain.EquipmentLogColumnEntity;
import com.laminar.equipment.domain.EquipmentLogColumnType;
import com.laminar.equipment.domain.EquipmentLogEntity;
import com.laminar.equipment.repository.EquipmentLogColumnRepository;
import com.laminar.equipment.repository.EquipmentLogRepository;
import com.laminar.equipment.repository.EquipmentRepository;
import com.laminar.web.error.ConflictException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장비 log 시트 — 동적 컬럼 정의 + 행 단위 값 JSONB.
 *
 * <p>Spec §2.10.10 + §2.10.12: text/number/enum/bool/datetime 5종 컬럼, key+label, priority로 정렬. 값은
 * JSONB로 컬럼 key → value 저장.
 */
@Service
public class EquipmentLogService {

  private static final int PRIORITY_STEP = 100;

  private final EquipmentLogColumnRepository columnRepo;
  private final EquipmentLogRepository logRepo;
  private final EquipmentRepository equipmentRepo;

  public EquipmentLogService(
      EquipmentLogColumnRepository columnRepo,
      EquipmentLogRepository logRepo,
      EquipmentRepository equipmentRepo) {
    this.columnRepo = columnRepo;
    this.logRepo = logRepo;
    this.equipmentRepo = equipmentRepo;
  }

  @Transactional
  public EquipmentLogColumnEntity createColumn(
      UUID equipmentId,
      String columnKey,
      String columnLabel,
      EquipmentLogColumnType columnType,
      List<String> enumValues,
      boolean required,
      String defaultValue) {
    WorkspaceContext ctx = requireWorkspaceWritable();
    equipmentRepo
        .findById(equipmentId)
        .filter(e -> e.getDeletedAt() == null)
        .filter(e -> ctx.ownsShared(e.getWorkspaceId()))
        .orElseThrow(() -> new IllegalArgumentException("equipment not found"));
    if (columnRepo
        .findByEquipmentIdAndColumnKeyAndDeletedAtIsNull(equipmentId, columnKey)
        .isPresent()) {
      throw new ConflictException("column key already exists: " + columnKey);
    }
    if (columnType == EquipmentLogColumnType.ENUM && (enumValues == null || enumValues.isEmpty())) {
      throw new IllegalArgumentException("enum column requires enum_values");
    }

    int nextPriority =
        columnRepo
            .findFirstByEquipmentIdAndDeletedAtIsNullOrderByPriorityDesc(equipmentId)
            .map(c -> c.getPriority() + PRIORITY_STEP)
            .orElse(PRIORITY_STEP);

    EquipmentLogColumnEntity column = new EquipmentLogColumnEntity();
    column.setWorkspaceId(ctx.workspaceId());
    column.setEquipmentId(equipmentId);
    column.setColumnKey(columnKey);
    column.setColumnLabel(columnLabel);
    column.setColumnType(columnType);
    column.setEnumValues(enumValues);
    column.setRequired(required);
    column.setPriority(nextPriority);
    column.setDefaultValue(defaultValue);
    return columnRepo.save(column);
  }

  @Transactional(readOnly = true)
  public List<EquipmentLogColumnEntity> listColumns(UUID equipmentId) {
    WorkspaceContextHolder.requirePersonal();
    return columnRepo.findByEquipmentIdAndDeletedAtIsNullOrderByPriorityAsc(equipmentId);
  }

  @Transactional
  public void softDeleteColumn(UUID columnId) {
    WorkspaceContext ctx = requireWorkspaceWritable();
    columnRepo
        .findById(columnId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsShared(c.getWorkspaceId()))
        .ifPresent(
            c -> {
              c.setDeletedAt(OffsetDateTime.now());
              columnRepo.save(c);
            });
  }

  @Transactional
  public EquipmentLogEntity log(
      UUID equipmentId,
      OffsetDateTime loggedAt,
      UUID reservationId,
      Map<String, Object> values,
      String notes) {
    WorkspaceContext ctx = requireWorkspaceWritable();
    equipmentRepo
        .findById(equipmentId)
        .filter(e -> e.getDeletedAt() == null)
        .filter(e -> ctx.ownsShared(e.getWorkspaceId()))
        .orElseThrow(() -> new IllegalArgumentException("equipment not found"));
    validateValues(equipmentId, values);

    EquipmentLogEntity entry = new EquipmentLogEntity();
    entry.setWorkspaceId(ctx.workspaceId());
    entry.setEquipmentId(equipmentId);
    entry.setLoggedBy(ctx.userId());
    entry.setLoggedAt(loggedAt == null ? OffsetDateTime.now() : loggedAt);
    entry.setReservationId(reservationId);
    entry.setValues(values == null ? new HashMap<>() : values);
    entry.setNotes(notes);
    return logRepo.save(entry);
  }

  @Transactional(readOnly = true)
  public List<EquipmentLogEntity> listLogs(UUID equipmentId) {
    WorkspaceContextHolder.requirePersonal();
    return logRepo.findByEquipmentIdAndDeletedAtIsNullOrderByLoggedAtDesc(equipmentId);
  }

  @Transactional(readOnly = true)
  public List<EquipmentLogEntity> listLogsInRange(
      UUID equipmentId, OffsetDateTime from, OffsetDateTime to) {
    WorkspaceContextHolder.requirePersonal();
    return logRepo.findByEquipmentIdAndLoggedAtBetweenAndDeletedAtIsNull(equipmentId, from, to);
  }

  /**
   * 필수 컬럼 누락 + 컬럼 type별 value 검증. type=number → numeric String, bool → true/false, datetime → ISO,
   * enum → enum_values 멤버.
   */
  private void validateValues(UUID equipmentId, Map<String, Object> values) {
    List<EquipmentLogColumnEntity> columns =
        columnRepo.findByEquipmentIdAndDeletedAtIsNullOrderByPriorityAsc(equipmentId);
    for (EquipmentLogColumnEntity col : columns) {
      Object raw = values == null ? null : values.get(col.getColumnKey());
      if (col.isRequired() && (raw == null || (raw instanceof String s && s.isBlank()))) {
        throw new IllegalArgumentException("required column missing: " + col.getColumnKey());
      }
      if (raw == null) continue;
      String value = String.valueOf(raw);
      switch (col.getColumnType()) {
        case NUMBER -> {
          try {
            Double.parseDouble(value);
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "column " + col.getColumnKey() + " expects number, got: " + value);
          }
        }
        case BOOL -> {
          if (!Objects.equals(value, "true") && !Objects.equals(value, "false")) {
            throw new IllegalArgumentException(
                "column " + col.getColumnKey() + " expects bool, got: " + value);
          }
        }
        case ENUM -> {
          List<String> allowed = col.getEnumValues();
          if (allowed == null || !allowed.contains(value)) {
            throw new IllegalArgumentException(
                "column " + col.getColumnKey() + " value not in enum_values: " + value);
          }
        }
        case DATETIME -> {
          try {
            OffsetDateTime.parse(value);
          } catch (Exception e) {
            throw new IllegalArgumentException(
                "column " + col.getColumnKey() + " expects ISO datetime, got: " + value);
          }
        }
        case TEXT -> {
          // free text
        }
      }
    }
  }

  private WorkspaceContext requireWorkspaceWritable() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.workspaceId() == null) {
      throw new IllegalStateException("workspace scope required");
    }
    if (ctx.scope() == WorkspaceContext.Scope.PERSONAL && !ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate equipment logs");
    }
    return ctx;
  }
}

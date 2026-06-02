package com.laminar.perpetual;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 영구노트 컬럼 정의 + 값 (시트형 동적 컬럼).
 *
 * <p>Spec §2.4.6: text/dropdown/checkbox 3종. board+user+name unique (DB partial unique on active
 * rows). 값은 TEXT 공통 (checkbox='true'/'false', dropdown=enum_values 중 하나).
 */
@Service
public class PerpetualColumnService {

  private static final int PRIORITY_STEP = 100;

  private final PerpetualColumnDefinitionRepository definitionRepo;
  private final PerpetualColumnRepository columnRepo;
  private final PerpetualNoteRepository noteRepo;

  public PerpetualColumnService(
      PerpetualColumnDefinitionRepository definitionRepo,
      PerpetualColumnRepository columnRepo,
      PerpetualNoteRepository noteRepo) {
    this.definitionRepo = definitionRepo;
    this.columnRepo = columnRepo;
    this.noteRepo = noteRepo;
  }

  @Transactional
  public PerpetualColumnDefinitionEntity createDefinition(
      UUID boardId, String name, PerpetualColumnType type, List<String> enumValues) {
    WorkspaceContext ctx = requirePersonalWritable();
    if (definitionRepo.findByBoardIdAndNameAndDeletedAtIsNull(boardId, name).isPresent()) {
      throw new IllegalStateException("column name already exists on board: " + name);
    }
    if (type == PerpetualColumnType.DROPDOWN && (enumValues == null || enumValues.isEmpty())) {
      throw new IllegalArgumentException("dropdown requires enum_values");
    }

    int nextPriority =
        definitionRepo
            .findFirstByBoardIdAndDeletedAtIsNullOrderByPriorityDesc(boardId)
            .map(d -> d.getPriority() + PRIORITY_STEP)
            .orElse(PRIORITY_STEP);

    PerpetualColumnDefinitionEntity definition = new PerpetualColumnDefinitionEntity();
    definition.setWorkspaceId(ctx.workspaceId());
    definition.setUserId(ctx.userId());
    definition.setCreatedBy(ctx.userId());
    definition.setBoardId(boardId);
    definition.setName(name);
    definition.setType(type);
    definition.setEnumValues(enumValues);
    definition.setPriority(nextPriority);
    return definitionRepo.save(definition);
  }

  @Transactional(readOnly = true)
  public List<PerpetualColumnDefinitionEntity> listDefinitionsByBoard(UUID boardId) {
    WorkspaceContextHolder.requirePersonal();
    return definitionRepo.findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(boardId);
  }

  @Transactional
  public void softDeleteDefinition(UUID definitionId) {
    WorkspaceContext ctx = requirePersonalWritable();
    definitionRepo
        .findById(definitionId)
        .filter(d -> d.getDeletedAt() == null)
        .filter(d -> ctx.ownsPersonal(d.getWorkspaceId(), d.getUserId()))
        .ifPresent(
            d -> {
              d.setDeletedAt(OffsetDateTime.now());
              definitionRepo.save(d);
            });
  }

  /** 값 upsert — 존재하면 update, 없으면 INSERT. dropdown은 enum_values 중 하나 검증. */
  @Transactional
  public PerpetualColumnEntity upsertValue(
      UUID perpetualNoteId, UUID columnDefinitionId, String value) {
    WorkspaceContext ctx = requirePersonalWritable();
    noteRepo
        .findById(perpetualNoteId)
        .filter(n -> n.getDeletedAt() == null)
        .filter(n -> ctx.ownsPersonal(n.getWorkspaceId(), n.getUserId()))
        .orElseThrow(() -> new IllegalArgumentException("perpetual note not found"));
    PerpetualColumnDefinitionEntity definition =
        definitionRepo
            .findById(columnDefinitionId)
            .filter(d -> d.getDeletedAt() == null)
            .filter(d -> ctx.ownsPersonal(d.getWorkspaceId(), d.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("column definition not found"));
    validateValue(definition, value);

    PerpetualColumnId id = new PerpetualColumnId(perpetualNoteId, columnDefinitionId);
    PerpetualColumnEntity column =
        columnRepo
            .findById(id)
            .orElseGet(
                () -> {
                  PerpetualColumnEntity created = new PerpetualColumnEntity();
                  created.setId(id);
                  return created;
                });
    column.setValue(value);
    return columnRepo.save(column);
  }

  @Transactional
  public void deleteValue(UUID perpetualNoteId, UUID columnDefinitionId) {
    WorkspaceContext ctx = requirePersonalWritable();
    // 값 엔티티는 @Filter 없는 junction → note 소유권 선검증 (cross-tenant 삭제 차단)
    noteRepo
        .findById(perpetualNoteId)
        .filter(n -> n.getDeletedAt() == null)
        .filter(n -> ctx.ownsPersonal(n.getWorkspaceId(), n.getUserId()))
        .orElseThrow(() -> new IllegalArgumentException("perpetual note not found"));
    columnRepo
        .findById(new PerpetualColumnId(perpetualNoteId, columnDefinitionId))
        .ifPresent(columnRepo::delete);
  }

  @Transactional(readOnly = true)
  public List<PerpetualColumnEntity> listValuesForNote(UUID perpetualNoteId) {
    WorkspaceContext ctx = WorkspaceContextHolder.requirePersonal();
    // 값 엔티티는 @Filter 없는 junction → note 소유권 선검증 (cross-tenant 열람 차단)
    noteRepo
        .findById(perpetualNoteId)
        .filter(n -> n.getDeletedAt() == null)
        .filter(n -> ctx.ownsPersonal(n.getWorkspaceId(), n.getUserId()))
        .orElseThrow(() -> new IllegalArgumentException("perpetual note not found"));
    return columnRepo.findByIdPerpetualNoteId(perpetualNoteId);
  }

  private void validateValue(PerpetualColumnDefinitionEntity definition, String value) {
    if (value == null) return;
    switch (definition.getType()) {
      case CHECKBOX -> {
        if (!Objects.equals(value, "true") && !Objects.equals(value, "false")) {
          throw new IllegalArgumentException("checkbox value must be 'true' or 'false'");
        }
      }
      case DROPDOWN -> {
        List<String> allowed = definition.getEnumValues();
        if (allowed == null || !allowed.contains(value)) {
          throw new IllegalArgumentException("dropdown value must be in enum_values: " + value);
        }
      }
      case TEXT -> {
        // free text
      }
    }
  }

  private WorkspaceContext requirePersonalWritable() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    if (!ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate columns");
    }
    return ctx;
  }
}

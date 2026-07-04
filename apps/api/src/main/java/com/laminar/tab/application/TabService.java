package com.laminar.tab.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.tab.domain.TabDefaultView;
import com.laminar.tab.domain.TabEntity;
import com.laminar.tab.domain.TabKind;
import com.laminar.tab.repository.TabRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보드 CRUD — PERSONAL scope 강제, Hibernate @Filter 자동 격리.
 *
 * <p>- create: priority = (현재 user의 max priority) + 100 (신규 보드는 항상 마지막에) - list: priority ASC,
 * deleted_at IS NULL - update: 변경 필드만 (name·default_view·icon·settings) - softDelete: deleted_at =
 * NOW (hard delete 절대 금지)
 *
 * <p>격리 정책: - 모든 메서드 진입 시 PERSONAL scope 강제 (canWrite 추가 검증은 mutation에서) - Hibernate @Filter가
 * SELECT WHERE subject_id=? AND user_id=? 자동 추가 - INSERT 시 subject_id·user_id는 context에서 명시 set
 * (필터는 SELECT 전용)
 */
@Service
public class TabService {

  private static final int PRIORITY_STEP = 100;

  private final TabRepository tabRepo;

  public TabService(TabRepository tabRepo) {
    this.tabRepo = tabRepo;
  }

  /** 종류 미지정 오버로드 — 기존 호출부(테스트 포함)용. kind는 DAG 기본. */
  @Transactional
  public TabEntity create(
      String name,
      String slug,
      TabDefaultView defaultView,
      String iconName,
      String iconColor,
      Map<String, Object> settings) {
    return create(name, slug, null, defaultView, iconName, iconColor, settings);
  }

  @Transactional
  public TabEntity create(
      String name,
      String slug,
      TabKind kind,
      TabDefaultView defaultView,
      String iconName,
      String iconColor,
      Map<String, Object> settings) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("boards");

    int nextPriority =
        tabRepo
            .findFirstByDeletedAtIsNullOrderByPriorityDesc()
            .map(b -> b.getPriority() + PRIORITY_STEP)
            .orElse(PRIORITY_STEP);

    TabEntity tab = new TabEntity();
    tab.setSubjectId(ctx.subjectId());
    tab.setUserId(ctx.userId());
    tab.setCreatedBy(ctx.userId());
    tab.setName(name);
    tab.setSlug(slug);
    tab.setKind(kind == null ? TabKind.DAG : kind);
    tab.setDefaultView(defaultView == null ? TabDefaultView.CALENDAR : defaultView);
    tab.setIconName(iconName);
    tab.setIconColor(iconColor);
    tab.setSettings(settings == null ? new HashMap<>() : settings);
    tab.setPriority(nextPriority);
    return tabRepo.save(tab);
  }

  @Transactional(readOnly = true)
  public List<TabEntity> listActive() {
    SubjectContextHolder.requirePersonal();
    return tabRepo.findByDeletedAtIsNullOrderByPriorityAsc();
  }

  @Transactional(readOnly = true)
  public Optional<TabEntity> findById(UUID tabId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonal();
    return tabRepo.findOwnedActive(tabId, ctx);
  }

  @Transactional
  public TabEntity update(
      UUID tabId,
      String name,
      TabDefaultView defaultView,
      String iconName,
      String iconColor,
      String bodyMd,
      Map<String, Object> settings) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("boards");
    TabEntity tab = tabRepo.findOwnedActiveOrThrow(tabId, ctx, "tab");

    if (name != null && !name.isBlank()) tab.setName(name);
    if (defaultView != null) tab.setDefaultView(defaultView);
    if (iconName != null) tab.setIconName(iconName);
    if (iconColor != null) tab.setIconColor(iconColor);
    if (bodyMd != null) tab.setBodyMd(bodyMd);
    if (settings != null) tab.setSettings(settings);
    return tabRepo.save(tab);
  }

  /**
   * DnD reorder — 클라이언트가 보낸 ID 순서대로 priority = (index+1) * 100 배치 UPDATE. 누락된 보드는 priority 보존
   * (clientside가 전체 목록 전송 안 한 경우 안전).
   */
  @Transactional
  public List<TabEntity> reorder(List<UUID> orderedTabIds) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("boards");
    if (orderedTabIds == null || orderedTabIds.isEmpty()) {
      return List.of();
    }
    List<TabEntity> result = new java.util.ArrayList<>(orderedTabIds.size());
    for (int i = 0; i < orderedTabIds.size(); i++) {
      UUID tabId = orderedTabIds.get(i);
      int newPriority = (i + 1) * PRIORITY_STEP;
      tabRepo
          .findOwnedActive(tabId, ctx)
          .ifPresent(
              b -> {
                b.setPriority(newPriority);
                result.add(tabRepo.save(b));
              });
    }
    return result;
  }

  @Transactional
  public void softDelete(UUID tabId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("boards");
    tabRepo
        .findOwnedActive(tabId, ctx)
        .ifPresent(
            tab -> {
              tab.setDeletedAt(OffsetDateTime.now());
              tabRepo.save(tab);
            });
  }
}

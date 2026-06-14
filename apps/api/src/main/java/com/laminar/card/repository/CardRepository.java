package com.laminar.card.repository;

import com.laminar.card.domain.CardEntity;
import com.laminar.common.repository.PersonalOwnedRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Card Repository (Personal-First — personalFirstFilter 자동 적용).
 *
 * <p>캘린더 뷰 hot path 메서드만 1차. RRULE expand·검색은 Phase 5+.
 */
public interface CardRepository extends PersonalOwnedRepository<CardEntity> {

  List<CardEntity> findByTabIdAndDeletedAtIsNullOrderByPriorityAsc(UUID tabId);

  List<CardEntity> findByTabIdAndStartDateBetweenAndDeletedAtIsNull(
      UUID tabId, LocalDate from, LocalDate to);

  java.util.Optional<CardEntity> findFirstByTabIdAndDeletedAtIsNullOrderByPriorityDesc(UUID tabId);

  /**
   * 멀티데이 카드 overlap 쿼리 — 시작일이 to 이전이고, (종료일이 from 이후 또는 종료일 없이 시작일이 from 이후). 미지정 카드 (start_date
   * NULL)는 캘린더 뷰 미노출.
   */
  @Query(
      """
            SELECT c FROM CardEntity c
            WHERE c.tabId = :tabId
              AND c.deletedAt IS NULL
              AND c.startDate IS NOT NULL
              AND c.startDate <= :to
              AND ((c.endDate IS NOT NULL AND c.endDate >= :from)
                   OR (c.endDate IS NULL AND c.startDate >= :from))
            ORDER BY c.startDate ASC, c.priority ASC
            """)
  List<CardEntity> findOverlappingByTabId(
      @Param("tabId") UUID tabId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  /**
   * 현행 과제 — 현재 컨텍스트(연구실·주제)의 미완료·미보관 카드. personalFirstFilter가 subject_id+user_id로 자동 격리하므로 작성자 본인
   * 것만(LAB이어도 카드는 Personal-First). 시작일 가까운 순(미지정은 뒤로), Pageable로 상한. 대시보드 '현행 과제' 섹션용.
   */
  @Query(
      """
            SELECT c FROM CardEntity c
            WHERE c.deletedAt IS NULL
              AND c.archivedAt IS NULL
              AND c.completed = false
            ORDER BY c.startDate ASC NULLS LAST, c.priority ASC
            """)
  List<CardEntity> findPending(Pageable pageable);

  /**
   * RRULE 마스터 카드 — origin=MANUAL이면서 rrule 있는 활성 카드. SYSTEM scope에서 호출 시 모든 user의 마스터 조회 (cron
   * worker용).
   */
  @Query(
      """
            SELECT c FROM CardEntity c
            WHERE c.deletedAt IS NULL
              AND c.rrule IS NOT NULL
              AND c.origin = com.laminar.card.domain.CardOrigin.MANUAL
              AND c.startDate IS NOT NULL
            """)
  List<CardEntity> findActiveRruleMasters();
}

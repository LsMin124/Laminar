package com.laminar.card.repository;

import com.laminar.card.domain.CardEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Card Repository (Personal-First — personalFirstFilter 자동 적용).
 *
 * <p>캘린더 뷰 hot path 메서드만 1차. RRULE expand·검색은 Phase 5+.
 */
public interface CardRepository extends JpaRepository<CardEntity, UUID> {

  List<CardEntity> findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(UUID boardId);

  List<CardEntity> findByBoardIdAndStartDateBetweenAndDeletedAtIsNull(
      UUID boardId, LocalDate from, LocalDate to);

  java.util.Optional<CardEntity> findFirstByBoardIdAndDeletedAtIsNullOrderByPriorityDesc(
      UUID boardId);

  /**
   * 멀티데이 카드 overlap 쿼리 — 시작일이 to 이전이고, (종료일이 from 이후 또는 종료일 없이 시작일이 from 이후). 미지정 카드 (start_date
   * NULL)는 캘린더 뷰 미노출.
   */
  @Query(
      """
            SELECT c FROM CardEntity c
            WHERE c.boardId = :boardId
              AND c.deletedAt IS NULL
              AND c.startDate IS NOT NULL
              AND c.startDate <= :to
              AND ((c.endDate IS NOT NULL AND c.endDate >= :from)
                   OR (c.endDate IS NULL AND c.startDate >= :from))
            ORDER BY c.startDate ASC, c.priority ASC
            """)
  List<CardEntity> findOverlappingByBoardId(
      @Param("boardId") UUID boardId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  /**
   * RRULE 마스터 카드 — origin=MANUAL이면서 rrule 있는 활성 카드. SYSTEM scope에서 호출 시 모든 user의 마스터 조회 (cron
   * worker용).
   */
  @Query(
      """
            SELECT c FROM CardEntity c
            WHERE c.deletedAt IS NULL
              AND c.rrule IS NOT NULL
              AND c.origin = com.laminar.card.CardOrigin.MANUAL
              AND c.startDate IS NOT NULL
            """)
  List<CardEntity> findActiveRruleMasters();
}

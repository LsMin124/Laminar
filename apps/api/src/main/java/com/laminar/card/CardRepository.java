package com.laminar.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Card Repository (Personal-First — personalFirstFilter 자동 적용).
 *
 * 캘린더 뷰 hot path 메서드만 1차. RRULE expand·검색은 Phase 5+.
 */
public interface CardRepository extends JpaRepository<CardEntity, UUID> {

    List<CardEntity> findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(UUID boardId);

    List<CardEntity> findByBoardIdAndStartDateBetweenAndDeletedAtIsNull(
            UUID boardId, LocalDate from, LocalDate to);
}

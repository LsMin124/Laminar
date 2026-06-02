package com.laminar.datememo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * date_memos Repository — Personal-First (workspace_id + user_id 자동 필터). 캘린더 뷰에서 month/week range
 * 조회가 hot path.
 */
public interface DateMemoRepository extends JpaRepository<DateMemoEntity, DateMemoId> {

  List<DateMemoEntity> findByIdBoardIdAndIdDateBetween(UUID boardId, LocalDate from, LocalDate to);
}

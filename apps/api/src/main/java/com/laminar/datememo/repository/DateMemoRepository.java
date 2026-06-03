package com.laminar.datememo.repository;

import com.laminar.datememo.domain.DateMemoEntity;
import com.laminar.datememo.domain.DateMemoId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * date_memos Repository — Personal-First (subject_id + user_id 자동 필터). 캘린더 뷰에서 month/week range 조회가
 * hot path.
 */
public interface DateMemoRepository extends JpaRepository<DateMemoEntity, DateMemoId> {

  List<DateMemoEntity> findByIdTabIdAndIdDateBetween(UUID tabId, LocalDate from, LocalDate to);
}

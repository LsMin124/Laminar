package com.laminar.datememo.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.datememo.domain.DateMemoEntity;
import com.laminar.datememo.domain.DateMemoId;
import com.laminar.datememo.repository.DateMemoRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * date_memos CRUD — Personal-First.
 *
 * <p>PK = (tab_id, user_id, date) 복합. body_md ≤ 10KB (Spec §2.5.2). upsert 패턴: 같은 (tab, user, date)
 * 키로 INSERT/UPDATE 자동.
 */
@Service
public class DateMemoService {

  private static final int MAX_BODY_LENGTH = 10_000;

  private final DateMemoRepository memoRepo;

  public DateMemoService(DateMemoRepository memoRepo) {
    this.memoRepo = memoRepo;
  }

  @Transactional
  public DateMemoEntity upsert(
      UUID tabId, LocalDate date, String bodyMd, Map<String, Object> attrs) {
    SubjectContext ctx = requirePersonalWritable();
    if (tabId == null || date == null) {
      throw new IllegalArgumentException("tabId and date required");
    }
    if (bodyMd != null && bodyMd.length() > MAX_BODY_LENGTH) {
      throw new IllegalArgumentException("body_md exceeds " + MAX_BODY_LENGTH + " chars");
    }

    DateMemoId id = new DateMemoId(tabId, ctx.userId(), date);
    DateMemoEntity memo =
        memoRepo
            .findById(id)
            .orElseGet(
                () -> {
                  DateMemoEntity created = new DateMemoEntity();
                  created.setId(id);
                  created.setSubjectId(ctx.subjectId());
                  return created;
                });
    memo.setBodyMd(bodyMd);
    memo.setAttrs(attrs == null ? new HashMap<>() : attrs);
    return memoRepo.save(memo);
  }

  @Transactional(readOnly = true)
  public Optional<DateMemoEntity> findByDate(UUID tabId, LocalDate date) {
    SubjectContext ctx = SubjectContextHolder.requirePersonal();
    return memoRepo.findById(new DateMemoId(tabId, ctx.userId(), date));
  }

  @Transactional(readOnly = true)
  public List<DateMemoEntity> listByTabDateRange(UUID tabId, LocalDate from, LocalDate to) {
    SubjectContextHolder.requirePersonal();
    if (from == null || to == null) {
      throw new IllegalArgumentException("from and to required");
    }
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to must be >= from");
    }
    return memoRepo.findByIdTabIdAndIdDateBetween(tabId, from, to);
  }

  @Transactional
  public void delete(UUID tabId, LocalDate date) {
    SubjectContext ctx = requirePersonalWritable();
    memoRepo.findById(new DateMemoId(tabId, ctx.userId(), date)).ifPresent(memoRepo::delete);
  }

  private SubjectContext requirePersonalWritable() {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.scope() != SubjectContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    if (!ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate date memos");
    }
    return ctx;
  }
}

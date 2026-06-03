package com.laminar.datememo.application;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
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
 * <p>PK = (board_id, user_id, date) 복합. body_md ≤ 10KB (Spec §2.5.2). upsert 패턴: 같은 (board, user,
 * date) 키로 INSERT/UPDATE 자동.
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
      UUID boardId, LocalDate date, String bodyMd, Map<String, Object> attrs) {
    WorkspaceContext ctx = requirePersonalWritable();
    if (boardId == null || date == null) {
      throw new IllegalArgumentException("boardId and date required");
    }
    if (bodyMd != null && bodyMd.length() > MAX_BODY_LENGTH) {
      throw new IllegalArgumentException("body_md exceeds " + MAX_BODY_LENGTH + " chars");
    }

    DateMemoId id = new DateMemoId(boardId, ctx.userId(), date);
    DateMemoEntity memo =
        memoRepo
            .findById(id)
            .orElseGet(
                () -> {
                  DateMemoEntity created = new DateMemoEntity();
                  created.setId(id);
                  created.setWorkspaceId(ctx.workspaceId());
                  return created;
                });
    memo.setBodyMd(bodyMd);
    memo.setAttrs(attrs == null ? new HashMap<>() : attrs);
    return memoRepo.save(memo);
  }

  @Transactional(readOnly = true)
  public Optional<DateMemoEntity> findByDate(UUID boardId, LocalDate date) {
    WorkspaceContext ctx = WorkspaceContextHolder.requirePersonal();
    return memoRepo.findById(new DateMemoId(boardId, ctx.userId(), date));
  }

  @Transactional(readOnly = true)
  public List<DateMemoEntity> listByBoardDateRange(UUID boardId, LocalDate from, LocalDate to) {
    WorkspaceContextHolder.requirePersonal();
    if (from == null || to == null) {
      throw new IllegalArgumentException("from and to required");
    }
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to must be >= from");
    }
    return memoRepo.findByIdBoardIdAndIdDateBetween(boardId, from, to);
  }

  @Transactional
  public void delete(UUID boardId, LocalDate date) {
    WorkspaceContext ctx = requirePersonalWritable();
    memoRepo.findById(new DateMemoId(boardId, ctx.userId(), date)).ifPresent(memoRepo::delete);
  }

  private WorkspaceContext requirePersonalWritable() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    if (!ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate date memos");
    }
    return ctx;
  }
}

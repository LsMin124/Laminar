package com.laminar.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laminar.workspace.WorkspaceRole;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** ThreadLocal 격리 + 실패-빠른 의미론 검증. */
class WorkspaceContextHolderTest {

  private static final UUID WORKSPACE = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @AfterEach
  void cleanup() {
    WorkspaceContextHolder.clear();
  }

  @Test
  void get_returns_null_when_unset() {
    WorkspaceContextHolder.clear();
    assertNull(WorkspaceContextHolder.get());
  }

  @Test
  void require_throws_when_unset() {
    WorkspaceContextHolder.clear();
    assertThrows(IllegalStateException.class, WorkspaceContextHolder::require);
  }

  @Test
  void set_then_get_returns_same_instance() {
    WorkspaceContext ctx = WorkspaceContext.personal(WORKSPACE, USER, WorkspaceRole.OWNER);
    WorkspaceContextHolder.set(ctx);

    assertEquals(ctx, WorkspaceContextHolder.get());
    assertEquals(ctx, WorkspaceContextHolder.require());
  }

  @Test
  void set_null_throws() {
    assertThrows(IllegalArgumentException.class, () -> WorkspaceContextHolder.set(null));
  }

  @Test
  void clear_removes_thread_local() {
    WorkspaceContextHolder.set(WorkspaceContext.system());
    WorkspaceContextHolder.clear();

    assertNull(WorkspaceContextHolder.get());
  }

  @Test
  void thread_isolation_no_leak_between_threads() throws InterruptedException {
    WorkspaceContext mainCtx = WorkspaceContext.personal(WORKSPACE, USER, WorkspaceRole.OWNER);
    WorkspaceContextHolder.set(mainCtx);

    AtomicReference<WorkspaceContext> otherThreadCtx = new AtomicReference<>();
    Thread other = new Thread(() -> otherThreadCtx.set(WorkspaceContextHolder.get()));
    other.start();
    other.join();

    assertNull(otherThreadCtx.get(), "ThreadLocal must isolate — other thread sees no context");
    assertNotNull(WorkspaceContextHolder.get(), "Main thread context preserved");
  }
}

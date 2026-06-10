package com.laminar.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** ThreadLocal 격리 + 실패-빠른 의미론 검증. */
class SubjectContextHolderTest {

  private static final UUID SUBJECT = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  void get_returns_null_when_unset() {
    SubjectContextHolder.clear();
    assertNull(SubjectContextHolder.get());
  }

  @Test
  void require_throws_when_unset() {
    SubjectContextHolder.clear();
    assertThrows(IllegalStateException.class, SubjectContextHolder::require);
  }

  @Test
  void set_then_get_returns_same_instance() {
    SubjectContext ctx = SubjectContext.personal(SUBJECT, USER, SubjectRole.OWNER);
    SubjectContextHolder.set(ctx);

    assertEquals(ctx, SubjectContextHolder.get());
    assertEquals(ctx, SubjectContextHolder.require());
  }

  @Test
  void set_null_throws() {
    assertThrows(IllegalArgumentException.class, () -> SubjectContextHolder.set(null));
  }

  @Test
  void clear_removes_thread_local() {
    SubjectContextHolder.set(SubjectContext.system());
    SubjectContextHolder.clear();

    assertNull(SubjectContextHolder.get());
  }

  @Test
  void thread_isolation_no_leak_between_threads() throws InterruptedException {
    SubjectContext mainCtx = SubjectContext.personal(SUBJECT, USER, SubjectRole.OWNER);
    SubjectContextHolder.set(mainCtx);

    AtomicReference<SubjectContext> otherThreadCtx = new AtomicReference<>();
    Thread other = new Thread(() -> otherThreadCtx.set(SubjectContextHolder.get()));
    other.start();
    other.join();

    assertNull(otherThreadCtx.get(), "ThreadLocal must isolate — other thread sees no context");
    assertNotNull(SubjectContextHolder.get(), "Main thread context preserved");
  }
}

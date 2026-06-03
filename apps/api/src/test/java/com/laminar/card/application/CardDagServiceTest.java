package com.laminar.card.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardRelationEntity;
import com.laminar.card.repository.CardRelationRepository;
import com.laminar.card.repository.CardRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * CardDagService 단위 테스트 — 사이클 검출·연쇄 이동·상류 검증 알고리즘.
 *
 * <p>리포지토리를 모킹해 DB 없이 순수 로직을 검증한다(컨텍스트 부팅 IT는 docker 필요라 별도).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardDagServiceTest {

  private static final UUID TAB = UUID.randomUUID();

  @Mock private CardRepository cardRepo;
  @Mock private CardRelationRepository relationRepo;

  private CardDagService service() {
    return new CardDagService(cardRepo, relationRepo);
  }

  private CardEntity card(UUID id, LocalDate start) {
    return card(id, start, null);
  }

  private CardEntity card(UUID id, LocalDate start, LocalDate end) {
    CardEntity c = new CardEntity();
    c.setId(id);
    c.setTabId(TAB);
    c.setStartDate(start);
    c.setEndDate(end);
    return c;
  }

  private CardRelationEntity edge(UUID from, UUID to) {
    CardRelationEntity r = new CardRelationEntity();
    r.setTabId(TAB);
    r.setFromCardId(from);
    r.setToCardId(to);
    r.setRelationKind("default");
    return r;
  }

  private void stub(List<CardEntity> cards, List<CardRelationEntity> relations) {
    when(cardRepo.findByTabIdAndDeletedAtIsNullOrderByPriorityAsc(TAB)).thenReturn(cards);
    when(relationRepo.findByTabIdAndDeletedAtIsNull(TAB)).thenReturn(relations);
  }

  // ---- wouldCreateCycle ----

  @Test
  void selfLoopIsCycle() {
    UUID a = UUID.randomUUID();
    assertTrue(service().wouldCreateCycle(TAB, a, a));
  }

  @Test
  void reverseEdgeOnExistingCreatesCycle() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    stub(List.of(), List.of(edge(a, b))); // A→B 존재
    // B→A 추가 시 to=A가 B(=from)에 도달 가능(A→B) → 사이클
    assertTrue(service().wouldCreateCycle(TAB, b, a));
  }

  @Test
  void indirectBackEdgeCreatesCycle() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    stub(List.of(), List.of(edge(a, b), edge(b, c))); // A→B→C
    // C→A 추가 시 to=A가 C에 도달(A→B→C) → 사이클
    assertTrue(service().wouldCreateCycle(TAB, c, a));
  }

  @Test
  void unrelatedEdgeIsNotCycle() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    stub(List.of(), List.of(edge(a, b))); // A→B
    // A→C 추가 시 to=C가 A에 도달 못함 → 사이클 아님
    assertFalse(service().wouldCreateCycle(TAB, a, c));
  }

  // ---- cascadeForward ----

  @Test
  void cascadeBumpsViolatingSuccessor() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    CardEntity ca = card(a, LocalDate.of(2026, 6, 5));
    CardEntity cb = card(b, LocalDate.of(2026, 6, 3));
    stub(List.of(ca, cb), List.of(edge(a, b)));
    List<CardEntity> moved = service().cascadeForward(TAB, a);
    assertEquals(LocalDate.of(2026, 6, 5), cb.getStartDate());
    assertEquals(List.of(cb), moved);
  }

  @Test
  void cascadePropagatesAlongChain() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    CardEntity ca = card(a, LocalDate.of(2026, 6, 5));
    CardEntity cb = card(b, LocalDate.of(2026, 6, 3));
    CardEntity cc = card(c, LocalDate.of(2026, 6, 1));
    stub(List.of(ca, cb, cc), List.of(edge(a, b), edge(b, c)));
    service().cascadeForward(TAB, a);
    assertEquals(LocalDate.of(2026, 6, 5), cb.getStartDate());
    assertEquals(LocalDate.of(2026, 6, 5), cc.getStartDate());
  }

  @Test
  void cascadePreservesSpan() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    CardEntity ca = card(a, LocalDate.of(2026, 6, 5));
    CardEntity cb = card(b, LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 4)); // span 1일
    stub(List.of(ca, cb), List.of(edge(a, b)));
    service().cascadeForward(TAB, a);
    assertEquals(LocalDate.of(2026, 6, 5), cb.getStartDate());
    assertEquals(LocalDate.of(2026, 6, 6), cb.getEndDate()); // +2일 시프트, span 보존
  }

  @Test
  void cascadeSkipsUndatedSuccessor() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    CardEntity ca = card(a, LocalDate.of(2026, 6, 5));
    CardEntity cb = card(b, null); // 날짜 미정
    stub(List.of(ca, cb), List.of(edge(a, b)));
    List<CardEntity> moved = service().cascadeForward(TAB, a);
    assertNull(cb.getStartDate());
    assertTrue(moved.isEmpty());
  }

  @Test
  void cascadeNoOpWhenSuccessorAlreadyValid() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    CardEntity ca = card(a, LocalDate.of(2026, 6, 1));
    CardEntity cb = card(b, LocalDate.of(2026, 6, 3)); // 이미 A 이후
    stub(List.of(ca, cb), List.of(edge(a, b)));
    List<CardEntity> moved = service().cascadeForward(TAB, a);
    assertEquals(LocalDate.of(2026, 6, 3), cb.getStartDate());
    assertTrue(moved.isEmpty());
  }

  @Test
  void cascadeDiamondTakesMaxPredecessor() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    UUID d = UUID.randomUUID();
    CardEntity ca = card(a, LocalDate.of(2026, 6, 5));
    CardEntity cb = card(b, LocalDate.of(2026, 6, 2));
    CardEntity cc = card(c, LocalDate.of(2026, 6, 3));
    CardEntity cd = card(d, LocalDate.of(2026, 6, 1));
    // A→B, A→C, B→D, C→D
    stub(List.of(ca, cb, cc, cd), List.of(edge(a, b), edge(a, c), edge(b, d), edge(c, d)));
    service().cascadeForward(TAB, a);
    assertEquals(LocalDate.of(2026, 6, 5), cb.getStartDate());
    assertEquals(LocalDate.of(2026, 6, 5), cc.getStartDate());
    assertEquals(LocalDate.of(2026, 6, 5), cd.getStartDate()); // max(B,C) = 6/5
  }

  // ---- maxPredecessorStart ----

  @Test
  void maxPredecessorStartReturnsLatest() {
    UUID a = UUID.randomUUID();
    UUID x = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    CardEntity ca = card(a, LocalDate.of(2026, 6, 3));
    CardEntity cx = card(x, LocalDate.of(2026, 6, 5));
    CardEntity cb = card(b, LocalDate.of(2026, 6, 1));
    stub(List.of(ca, cx, cb), List.of(edge(a, b), edge(x, b))); // A→B, X→B
    assertEquals(LocalDate.of(2026, 6, 5), service().maxPredecessorStart(TAB, b));
  }

  @Test
  void maxPredecessorStartNullWhenNoPredecessor() {
    UUID a = UUID.randomUUID();
    CardEntity ca = card(a, LocalDate.of(2026, 6, 3));
    stub(List.of(ca), List.of());
    assertNull(service().maxPredecessorStart(TAB, a));
  }
}

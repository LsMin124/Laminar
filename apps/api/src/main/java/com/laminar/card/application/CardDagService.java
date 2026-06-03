package com.laminar.card.application;

import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardRelationEntity;
import com.laminar.card.repository.CardRelationRepository;
import com.laminar.card.repository.CardRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 DAG 불변식 — 시간 강제 + 연쇄 이동 + 비순환 (DAG 개편 Phase 2).
 *
 * <p>엣지(card_relations) {@code A→B ⟹ B.startDate ≥ A.startDate}. DAG 방향 = 시간 흐름. 위반 시 후행 노드를 최소
 * 시프트(필요한 노드만, span 보존)로 연쇄 이동한다. 날짜 미정(start=null) 카드는 강제 대상에서 제외(설계 §5 — x 위치 정책 Phase 4).
 *
 * <p>현재 전 relation_kind를 시간 강제한다(설계 §2.2 ②). kind별 분기(sequence=강제 / related=자유)는 {@link
 * #isTimeEnforced}만 바꾸면 된다.
 *
 * <p>모든 메서드는 호출 측 트랜잭션(PERSONAL scope) 안에서 동작하며, personalFirstFilter가 tab 단위 조회를 사용자별로 자동 격리한다. 따라서
 * PK 우회 로드를 쓰지 않고 tab 쿼리만 사용한다.
 */
@Service
public class CardDagService {

  private final CardRepository cardRepo;
  private final CardRelationRepository relationRepo;

  public CardDagService(CardRepository cardRepo, CardRelationRepository relationRepo) {
    this.cardRepo = cardRepo;
    this.relationRepo = relationRepo;
  }

  /**
   * 엣지 {@code from→to} 추가가 사이클을 만드는지 — {@code to}에서 {@code from}으로 가는 경로가 이미 존재하면 true. 동일
   * 카드(self)도 사이클로 간주.
   */
  @Transactional(readOnly = true)
  public boolean wouldCreateCycle(UUID tabId, UUID fromCardId, UUID toCardId) {
    if (fromCardId.equals(toCardId)) {
      return true;
    }
    Map<UUID, List<UUID>> adjacency = buildAdjacency(tabId);
    Set<UUID> visited = new HashSet<>();
    ArrayDeque<UUID> queue = new ArrayDeque<>();
    queue.add(toCardId);
    while (!queue.isEmpty()) {
      UUID current = queue.poll();
      if (current.equals(fromCardId)) {
        return true;
      }
      if (!visited.add(current)) {
        continue;
      }
      for (UUID next : adjacency.getOrDefault(current, List.of())) {
        if (!visited.contains(next)) {
          queue.add(next);
        }
      }
    }
    return false;
  }

  /** {@code cardId}의 선행 카드들 startDate 중 최댓값 (없으면 null). 카드를 선행보다 앞당기지 못하게 하는 상류 검증용. */
  @Transactional(readOnly = true)
  public LocalDate maxPredecessorStart(UUID tabId, UUID cardId) {
    Map<UUID, CardEntity> cards = cardsById(tabId);
    LocalDate max = null;
    for (CardRelationEntity relation : relationRepo.findByTabIdAndDeletedAtIsNull(tabId)) {
      if (!cardId.equals(relation.getToCardId()) || !isTimeEnforced(relation.getRelationKind())) {
        continue;
      }
      CardEntity predecessor = cards.get(relation.getFromCardId());
      if (predecessor == null || predecessor.getStartDate() == null) {
        continue;
      }
      if (max == null || predecessor.getStartDate().isAfter(max)) {
        max = predecessor.getStartDate();
      }
    }
    return max;
  }

  /**
   * {@code rootCardId}의 startDate가 바뀐(또는 새 엣지가 생긴) 뒤, 후행 노드를 {@code B.start ≥ A.start}로 연쇄 이동한다.
   * 위반하는 후행만 최소 시프트하며 span(start~end)은 보존한다. 이동된 카드 목록을 반환한다(미이동 시 빈 목록).
   */
  @Transactional
  public List<CardEntity> cascadeForward(UUID tabId, UUID rootCardId) {
    Map<UUID, CardEntity> cards = cardsById(tabId);
    Map<UUID, List<UUID>> adjacency = buildAdjacency(tabId);

    List<CardEntity> moved = new ArrayList<>();
    Set<UUID> movedIds = new HashSet<>();
    ArrayDeque<UUID> queue = new ArrayDeque<>();
    queue.add(rootCardId);

    // DAG라 단조 증가로 수렴하지만, 데이터에 사이클이 있어도 무한 루프하지 않도록 방어적 상한.
    int iterations = 0;
    int maxIterations = (cards.size() + 1) * (cards.size() + 1) + 16;
    while (!queue.isEmpty()) {
      if (++iterations > maxIterations) {
        throw new IllegalStateException(
            "card DAG cascade did not converge (cycle in data?) tab=" + tabId);
      }
      CardEntity current = cards.get(queue.poll());
      if (current == null || current.getStartDate() == null) {
        continue;
      }
      LocalDate currentStart = current.getStartDate();
      for (UUID successorId : adjacency.getOrDefault(current.getId(), List.of())) {
        CardEntity successor = cards.get(successorId);
        if (successor == null || successor.getStartDate() == null) {
          continue; // 날짜 미정 카드엔 강제하지 않음
        }
        if (successor.getStartDate().isBefore(currentStart)) {
          long delta = ChronoUnit.DAYS.between(successor.getStartDate(), currentStart);
          successor.setStartDate(currentStart);
          if (successor.getEndDate() != null) {
            successor.setEndDate(successor.getEndDate().plusDays(delta)); // span 보존
          }
          if (movedIds.add(successorId)) {
            moved.add(successor);
          }
          queue.add(successorId);
        }
      }
    }
    if (!moved.isEmpty()) {
      cardRepo.saveAll(moved);
    }
    return moved;
  }

  /** 현재 전 relation_kind 시간 강제(설계 §2.2 ②). kind별 분기 시 이 메서드만 변경. */
  private boolean isTimeEnforced(String relationKind) {
    return true;
  }

  private Map<UUID, List<UUID>> buildAdjacency(UUID tabId) {
    Map<UUID, List<UUID>> adjacency = new HashMap<>();
    for (CardRelationEntity relation : relationRepo.findByTabIdAndDeletedAtIsNull(tabId)) {
      if (!isTimeEnforced(relation.getRelationKind())) {
        continue;
      }
      adjacency
          .computeIfAbsent(relation.getFromCardId(), key -> new ArrayList<>())
          .add(relation.getToCardId());
    }
    return adjacency;
  }

  private Map<UUID, CardEntity> cardsById(UUID tabId) {
    Map<UUID, CardEntity> cards = new HashMap<>();
    for (CardEntity card : cardRepo.findByTabIdAndDeletedAtIsNullOrderByPriorityAsc(tabId)) {
      cards.put(card.getId(), card);
    }
    return cards;
  }
}

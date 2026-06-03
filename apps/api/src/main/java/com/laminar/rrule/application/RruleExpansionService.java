package com.laminar.rrule.application;

import com.laminar.card.CardEntity;
import com.laminar.card.CardImportance;
import com.laminar.card.CardOrigin;
import com.laminar.card.CardRepository;
import com.laminar.rrule.domain.Rrule;
import com.laminar.rrule.domain.RruleParser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RRULE 마스터 카드 → 인스턴스 카드 (origin=rrule_expansion) expand.
 *
 * <p>멱등성: 마스터 카드 + start_date 키로 attrs.rrule_master_id + attrs.rrule_occurrence_date 사용. 같은
 * (master, date) 인스턴스가 이미 있으면 skip — 매 cron 호출이 안전.
 *
 * <p>Personal-First 격리는 호출자 책임 (cron worker가 마스터 카드 user 컨텍스트로 진입).
 */
@Service
public class RruleExpansionService {

  private static final Logger log = LoggerFactory.getLogger(RruleExpansionService.class);

  static final String ATTR_MASTER_ID = "rrule_master_id";
  static final String ATTR_OCCURRENCE_DATE = "rrule_occurrence_date";

  private final CardRepository cardRepo;

  public RruleExpansionService(CardRepository cardRepo) {
    this.cardRepo = cardRepo;
  }

  /**
   * 마스터 카드 1개를 expand. 이미 생성된 인스턴스는 skip.
   *
   * @return 생성된 신규 인스턴스 카드 개수
   */
  @Transactional
  public int expandMaster(CardEntity master, LocalDate windowStart, LocalDate windowEnd) {
    if (master == null || master.getRrule() == null || master.getRrule().isBlank()) {
      return 0;
    }
    if (master.getStartDate() == null) {
      log.warn("rrule expand: master {} has no start_date — skip", master.getId());
      return 0;
    }

    Rrule rrule = RruleParser.parse(master.getRrule());
    List<LocalDate> occurrences = rrule.expand(master.getStartDate(), windowStart, windowEnd);
    if (occurrences.isEmpty()) return 0;

    // 같은 board+master 기존 인스턴스의 occurrence_date 수집 — 중복 방지
    Set<String> existingDates = new HashSet<>();
    for (CardEntity existing :
        cardRepo.findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(master.getBoardId())) {
      if (existing.getOrigin() != CardOrigin.RRULE_EXPANSION) continue;
      Map<String, Object> attrs = existing.getAttrs();
      if (attrs == null) continue;
      if (!master.getId().toString().equals(String.valueOf(attrs.get(ATTR_MASTER_ID)))) continue;
      Object date = attrs.get(ATTR_OCCURRENCE_DATE);
      if (date != null) existingDates.add(String.valueOf(date));
    }

    int created = 0;
    List<LocalDate> toCreate = new ArrayList<>();
    for (LocalDate occurrence : occurrences) {
      if (occurrence.equals(master.getStartDate())) continue; // 마스터 자체 일자 skip
      if (existingDates.contains(occurrence.toString())) continue;
      toCreate.add(occurrence);
    }

    for (LocalDate date : toCreate) {
      CardEntity instance = cloneFromMaster(master, date);
      cardRepo.save(instance);
      created++;
    }
    if (created > 0) {
      log.info(
          "rrule expand: master={} created {} instances ({}~{})",
          master.getId(),
          created,
          windowStart,
          windowEnd);
    }
    return created;
  }

  private CardEntity cloneFromMaster(CardEntity master, LocalDate occurrence) {
    CardEntity instance = new CardEntity();
    instance.setWorkspaceId(master.getWorkspaceId());
    instance.setUserId(master.getUserId());
    instance.setCreatedBy(master.getUserId());
    instance.setBoardId(master.getBoardId());
    instance.setTitle(master.getTitle());
    instance.setBodyMd(master.getBodyMd());
    instance.setStartDate(occurrence);
    if (master.getEndDate() != null && master.getStartDate() != null) {
      long span =
          java.time.temporal.ChronoUnit.DAYS.between(master.getStartDate(), master.getEndDate());
      instance.setEndDate(occurrence.plusDays(span));
    }
    instance.setStartTime(master.getStartTime());
    instance.setAllDay(master.isAllDay());
    instance.setTimeZone(master.getTimeZone());
    instance.setImportance(
        master.getImportance() == null ? CardImportance.NORMAL : master.getImportance());
    instance.setRrule(null);
    instance.setOrigin(CardOrigin.RRULE_EXPANSION);
    instance.setPriority(master.getPriority());

    Map<String, Object> attrs =
        new HashMap<>(master.getAttrs() == null ? Map.of() : master.getAttrs());
    attrs.put(ATTR_MASTER_ID, master.getId().toString());
    attrs.put(ATTR_OCCURRENCE_DATE, occurrence.toString());
    instance.setAttrs(attrs);
    return instance;
  }
}

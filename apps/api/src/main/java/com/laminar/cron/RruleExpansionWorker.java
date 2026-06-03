package com.laminar.cron;

import com.laminar.card.domain.CardEntity;
import com.laminar.card.repository.CardRepository;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.rrule.application.RruleExpansionService;
import java.time.LocalDate;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RRULE 마스터 카드 expand cron — 매일 04:00 KST.
 *
 * <p>향후 90일 window까지 인스턴스 카드 생성 (origin=rrule_expansion). 멱등: RruleExpansionService가
 * attrs.rrule_master_id + occurrence_date로 중복 방지.
 *
 * <p>SYSTEM scope로 진입 — 모든 user의 마스터 카드 처리 (Personal-First filter 미활성).
 */
@Component
public class RruleExpansionWorker {

  private static final Logger log = LoggerFactory.getLogger(RruleExpansionWorker.class);
  private static final int FUTURE_WINDOW_DAYS = 90;

  private final CardRepository cardRepo;
  private final RruleExpansionService expansionService;
  private final HibernateFilterActivator filterActivator;

  public RruleExpansionWorker(
      CardRepository cardRepo,
      RruleExpansionService expansionService,
      HibernateFilterActivator filterActivator) {
    this.cardRepo = cardRepo;
    this.expansionService = expansionService;
    this.filterActivator = filterActivator;
  }

  @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
  @SchedulerLock(name = "rruleExpansion", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
  public void runExpansion() {
    WorkspaceContextHolder.set(WorkspaceContext.system());
    filterActivator.activate();
    try {
      LocalDate windowStart = LocalDate.now();
      LocalDate windowEnd = windowStart.plusDays(FUTURE_WINDOW_DAYS);
      List<CardEntity> masters = cardRepo.findActiveRruleMasters();
      int totalCreated = 0;
      int processed = 0;
      int errors = 0;
      for (CardEntity master : masters) {
        try {
          totalCreated += expansionService.expandMaster(master, windowStart, windowEnd);
          processed++;
        } catch (Exception e) {
          errors++;
          log.warn("rrule expand failed master={}: {}", master.getId(), e.getMessage());
        }
      }
      log.info(
          "rrule expansion: masters={}, processed={}, instances_created={}, errors={}",
          masters.size(),
          processed,
          totalCreated,
          errors);
    } finally {
      WorkspaceContextHolder.clear();
    }
  }
}

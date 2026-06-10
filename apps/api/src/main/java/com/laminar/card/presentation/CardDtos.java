package com.laminar.card.presentation;

import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardImportance;
import com.laminar.card.domain.CardOrigin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CardDtos {

  private CardDtos() {}

  /**
   * 마크다운 본문 → 카드 미리보기용 평문 발췌. 그래프 페이로드 경감: TabGraph는 전체 bodyMd 대신 이것만 싣는다. 앞 240자만 처리 후
   * 코드/이미지/링크/수식/강조/줄머리 기호 제거 + 공백 정규화(프론트 mdExcerpt 미러). 빈/공백 본문이면 null(프론트 '빈 문서'), 이미지전용 등 발췌가
   * 비면 "…".
   */
  public static String bodyExcerpt(String md) {
    if (md == null || md.isBlank()) return null;
    String s = md.length() > 240 ? md.substring(0, 240) : md;
    s =
        s.replaceAll("(?s)```.*?```", " ")
            .replaceAll("`([^`]*)`", "$1")
            .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
            .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
            .replaceAll("\\$\\$?[^$]*\\$\\$?", " ")
            .replaceAll("(?m)^[ \\t>#+-]*", "")
            .replaceAll("[*_~]", "")
            .replaceAll("\\s+", " ")
            .trim();
    return s.isEmpty() ? "…" : s;
  }

  public record CreateRequest(
      UUID tabId,
      @NotBlank @Size(max = 200) String title,
      @Size(max = 100) String slug,
      String bodyMd,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      Boolean allDay,
      @Size(max = 60) String timeZone,
      CardImportance importance,
      @Size(max = 500) String rrule,
      CardOrigin origin,
      Map<String, Object> attrs) {}

  public record UpdateRequest(
      @Size(max = 200) String title,
      String bodyMd,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      Boolean allDay,
      @Size(max = 60) String timeZone,
      CardImportance importance,
      @Size(max = 500) String rrule,
      Boolean completed,
      Map<String, Object> attrs,
      Double canvasY) {}

  public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {}

  /** 카드 카테고리 지정/해제 — categoryId null이면 미분류. */
  public record SetCategoryRequest(UUID categoryId) {}

  public record RenderedBodyResponse(UUID cardId, String html) {}

  public record CardResponse(
      UUID id,
      UUID subjectId,
      UUID userId,
      UUID tabId,
      String title,
      String slug,
      String bodyMd,
      String bodyExcerpt,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      boolean allDay,
      String timeZone,
      CardImportance importance,
      boolean completed,
      String rrule,
      CardOrigin origin,
      int priority,
      Map<String, Object> attrs,
      OffsetDateTime archivedAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      Double canvasY) {

    /** 단건 응답 — 전체 bodyMd 포함(발췌 null). 위치 인자 23개 직접 나열 금지, 본 팩토리 경유(DX-7). */
    public static CardResponse of(CardEntity c) {
      return build(c, c.getBodyMd(), null);
    }

    /** 그래프 응답 — 페이로드 경감: bodyMd 제외, 서버 산출 발췌만(전체 본문은 GET /api/cards/{id}). */
    public static CardResponse excerptOf(CardEntity c) {
      // 한정 호출 — 비한정 bodyExcerpt는 레코드 자신의 컴포넌트 접근자(무인자)로 해석된다.
      return build(c, null, CardDtos.bodyExcerpt(c.getBodyMd()));
    }

    private static CardResponse build(CardEntity c, String bodyMd, String excerpt) {
      return new CardResponse(
          c.getId(),
          c.getSubjectId(),
          c.getUserId(),
          c.getTabId(),
          c.getTitle(),
          c.getSlug(),
          bodyMd,
          excerpt,
          c.getStartDate(),
          c.getEndDate(),
          c.getStartTime(),
          c.isAllDay(),
          c.getTimeZone(),
          c.getImportance(),
          c.isCompleted(),
          c.getRrule(),
          c.getOrigin(),
          c.getPriority(),
          c.getAttrs(),
          c.getArchivedAt(),
          c.getCreatedAt(),
          c.getUpdatedAt(),
          c.getCanvasY());
    }
  }
}

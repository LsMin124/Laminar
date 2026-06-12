package com.laminar.card.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.laminar.card.application.CardService;
import com.laminar.card.domain.CardEntity;
import com.laminar.error.BadRequestException;
import com.laminar.error.ConflictException;
import com.laminar.error.ErrorCode;
import com.laminar.markdown.MarkdownService;
import com.laminar.testsupport.WebTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /api/cards 매핑·검증·상태코드 (R2²) — DX-4 오류 code 계약(CARD_*)의 HTTP 직렬화 검증 포함.
 *
 * <p>DAG 시간강제·연쇄이동 정책은 CardDagServiceTest·IT 담당.
 */
class CardControllerTest {

  private CardService cardService;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    cardService = mock(CardService.class);
    mvc = WebTestSupport.mvc(new CardController(cardService, mock(MarkdownService.class)));
  }

  private static CardEntity card(String title) {
    CardEntity c = new CardEntity();
    c.setId(UUID.randomUUID());
    c.setTabId(UUID.randomUUID());
    c.setTitle(title);
    c.setBodyMd("# 본문");
    c.setStartDate(LocalDate.of(2026, 6, 12));
    return c;
  }

  @Test
  void 카드_생성은_200과_전체_본문을_반환한다() throws Exception {
    given(cardService.create(any())).willReturn(card("실험 준비"));

    mvc.perform(
            post("/api/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"실험 준비\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("실험 준비"))
        .andExpect(jsonPath("$.bodyMd").value("# 본문"))
        .andExpect(jsonPath("$.startDate").value("2026-06-12"));
  }

  @Test
  void 카드_생성_제목_누락은_400() throws Exception {
    mvc.perform(
            post("/api/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bodyMd\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("title")));
  }

  @Test
  void 카드_생성_스팬_초과는_400과_CARD_SPAN_EXCEEDED_code() throws Exception {
    given(cardService.create(any()))
        .willThrow(
            new BadRequestException("date span exceeds 60 days", ErrorCode.CARD_SPAN_EXCEEDED));

    mvc.perform(
            post("/api/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"장기 실험\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-12-31\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CARD_SPAN_EXCEEDED"));
  }

  @Test
  void 카드_수정_선행_위반은_409와_CARD_BEFORE_PREDECESSOR_code() throws Exception {
    given(cardService.update(any(), any()))
        .willThrow(
            new ConflictException(
                "cannot move card before its predecessor", ErrorCode.CARD_BEFORE_PREDECESSOR));

    mvc.perform(
            patch("/api/cards/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2026-01-01\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CARD_BEFORE_PREDECESSOR"));
  }

  @Test
  void 카드_단건_부재는_404() throws Exception {
    given(cardService.findById(any())).willReturn(Optional.empty());

    mvc.perform(get("/api/cards/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void 카드_삭제는_204_위임() throws Exception {
    UUID cardId = UUID.randomUUID();

    mvc.perform(delete("/api/cards/{id}", cardId)).andExpect(status().isNoContent());

    verify(cardService).softDelete(cardId);
  }

  @Test
  void 탭_카드_목록은_from_to_쌍이_있으면_범위_조회로_분기한다() throws Exception {
    UUID tabId = UUID.randomUUID();
    given(cardService.listByTabAndDateRange(any(), any(), any())).willReturn(List.of());

    mvc.perform(get("/api/tabs/{tabId}/cards?from=2026-06-01&to=2026-06-30", tabId))
        .andExpect(status().isOk());

    verify(cardService)
        .listByTabAndDateRange(tabId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
  }

  @Test
  void 탭_카드_목록은_from만_있으면_전체_조회한다() throws Exception {
    UUID tabId = UUID.randomUUID();
    given(cardService.listByTab(any())).willReturn(List.of());

    mvc.perform(get("/api/tabs/{tabId}/cards?from=2026-06-01", tabId)).andExpect(status().isOk());

    verify(cardService).listByTab(tabId);
  }

  @Test
  void 탭_카드_목록_날짜_형식_오류는_400() throws Exception {
    mvc.perform(get("/api/tabs/{tabId}/cards?from=06-01-2026&to=2026-06-30", UUID.randomUUID()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("요청 경로 또는 파라미터 형식이 올바르지 않습니다"));
  }

  @Test
  void 카드_reorder_빈_목록은_400() throws Exception {
    mvc.perform(
            patch("/api/tabs/{tabId}/cards/reorder", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderedIds\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("orderedIds")));
  }
}

package com.laminar.card.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.laminar.card.application.CardRelationService;
import com.laminar.card.domain.CardRelationEntity;
import com.laminar.error.ConflictException;
import com.laminar.error.ErrorCode;
import com.laminar.testsupport.WebTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** /api/card-relations 매핑·검증·상태코드 (R2²) — CARD_CYCLE envelope 전체 형상 단언 포함. */
class CardRelationControllerTest {

  private CardRelationService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(CardRelationService.class);
    mvc = WebTestSupport.mvc(new CardRelationController(service));
  }

  @Test
  void 관계_생성_사이클은_409_envelope_전체_형상() throws Exception {
    given(service.create(any(), any(), any(), any(), any(), any()))
        .willThrow(new ConflictException("relation would create a cycle", ErrorCode.CARD_CYCLE));

    mvc.perform(
            post("/api/card-relations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fromCardId\":\""
                        + UUID.randomUUID()
                        + "\",\"toCardId\":\""
                        + UUID.randomUUID()
                        + "\"}"))
        .andExpect(status().isConflict())
        // DX-4 계약: FE apiErrors.ts가 의존하는 envelope 필드 전체를 고정한다.
        .andExpect(jsonPath("$.timestamp").value(notNullValue()))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.error").value("Conflict"))
        .andExpect(jsonPath("$.message").value("relation would create a cycle"))
        .andExpect(jsonPath("$.path").value("/api/card-relations"))
        .andExpect(jsonPath("$.code").value("CARD_CYCLE"));
  }

  @Test
  void 관계_생성_fromCardId_누락은_400() throws Exception {
    mvc.perform(
            post("/api/card-relations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toCardId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("fromCardId")));
  }

  @Test
  void 엣지_라벨_수정은_200과_summary를_반환한다() throws Exception {
    UUID relationId = UUID.randomUUID();
    CardRelationEntity updated = new CardRelationEntity();
    updated.setId(relationId);
    updated.setSummary("준비 완료 후");
    given(service.update(eq(relationId), eq("준비 완료 후"))).willReturn(updated);

    mvc.perform(
            patch("/api/card-relations/{id}", relationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\":\"준비 완료 후\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary").value("준비 완료 후"));
  }
}

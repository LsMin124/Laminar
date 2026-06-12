package com.laminar.subject.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.laminar.error.NotFoundException;
import com.laminar.subject.application.LabJoinService;
import com.laminar.subject.domain.LabInviteCodeEntity;
import com.laminar.subject.domain.LabJoinStatus;
import com.laminar.testsupport.WebTestSupport;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LAB 가입 흐름 HTTP 표면 (R2² — LAB재설계 §2 신설 표면).
 *
 * <p>코드 검증·승인 정책 자체는 {@code LabJoinFlowIT}가 담당 — 여기서는 매핑·검증·상태코드·envelope 변환만.
 */
class LabJoinControllerTest {

  private LabJoinService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(LabJoinService.class);
    mvc = WebTestSupport.mvc(new LabJoinController(service));
  }

  @Test
  void join_유효한_코드는_200과_가입_결과를_반환한다() throws Exception {
    UUID labId = UUID.randomUUID();
    given(service.join("ABCD2345", WebTestSupport.PRINCIPAL.userId()))
        .willReturn(new LabJoinService.JoinOutcome(labId, "광학연구실", LabJoinStatus.PENDING));

    mvc.perform(
            post("/api/labs/join")
                .principal(WebTestSupport.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ABCD2345\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.labId").value(labId.toString()))
        .andExpect(jsonPath("$.labName").value("광학연구실"))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void join_빈_코드는_400_검증_오류() throws Exception {
    mvc.perform(
            post("/api/labs/join")
                .principal(WebTestSupport.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value(containsString("code")))
        .andExpect(jsonPath("$.path").value("/api/labs/join"));
  }

  @Test
  void join_무효한_코드는_404_envelope로_변환된다() throws Exception {
    given(service.join(any(), any())).willThrow(new NotFoundException("유효하지 않은 초대코드입니다"));

    mvc.perform(
            post("/api/labs/join")
                .principal(WebTestSupport.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"WRONG123\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("유효하지 않은 초대코드입니다"))
        .andExpect(jsonPath("$.path").value("/api/labs/join"));
  }

  @Test
  void join_미인증_요청은_403_일반화_메시지() throws Exception {
    // requirePrincipal의 IllegalStateException → 블랭킷 403 (내부 메시지 비노출).
    mvc.perform(
            post("/api/labs/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ABCD2345\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("요청을 수행할 권한이 없습니다"));
  }

  @Test
  void 초대코드_재발급은_200과_새_코드를_반환한다() throws Exception {
    LabInviteCodeEntity code = new LabInviteCodeEntity();
    code.setCode("XYZ23456");
    given(service.rotateInviteCode(WebTestSupport.PRINCIPAL.userId())).willReturn(code);

    mvc.perform(post("/api/subjects/current/lab/invite-code").principal(WebTestSupport.auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("XYZ23456"));
  }

  @Test
  void 현재_초대코드_미발급이면_code_null로_200() throws Exception {
    given(service.currentInviteCode()).willReturn(Optional.empty());

    mvc.perform(get("/api/subjects/current/lab/invite-code"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value((String) null));
  }

  @Test
  void 가입_승인은_204이고_요청자와_승인자를_서비스에_위임한다() throws Exception {
    UUID requestId = UUID.randomUUID();

    mvc.perform(
            post("/api/subjects/current/lab/join-requests/{id}/approve", requestId)
                .principal(WebTestSupport.auth()))
        .andExpect(status().isNoContent());

    verify(service).approve(requestId, WebTestSupport.PRINCIPAL.userId());
  }

  @Test
  void 가입_거절도_204_위임() throws Exception {
    UUID requestId = UUID.randomUUID();

    mvc.perform(
            post("/api/subjects/current/lab/join-requests/{id}/reject", requestId)
                .principal(WebTestSupport.auth()))
        .andExpect(status().isNoContent());

    verify(service).reject(requestId, WebTestSupport.PRINCIPAL.userId());
  }

  @Test
  void 승인_경로의_UUID_형식_오류는_500이_아니라_400() throws Exception {
    // R2² 발견 결함 회귀 방지: 전용 핸들러 부재 시 catch-all이 500으로 삼켰다.
    mvc.perform(
            post("/api/subjects/current/lab/join-requests/not-a-uuid/approve")
                .principal(WebTestSupport.auth()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("요청 경로 또는 파라미터 형식이 올바르지 않습니다"));
  }

  @Test
  void join_표면의_미지원_메서드는_405() throws Exception {
    // R2² 발견 결함 회귀 방지: HttpRequestMethodNotSupported가 500으로 새지 않는다.
    mvc.perform(delete("/api/labs/join").principal(WebTestSupport.auth()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.status").value(405));
  }

  @Test
  void join_JSON_아닌_ContentType은_415() throws Exception {
    mvc.perform(
            post("/api/labs/join")
                .principal(WebTestSupport.auth())
                .contentType(MediaType.TEXT_PLAIN)
                .content("code=ABCD2345"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.status").value(415));
  }
}

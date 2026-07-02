package com.laminar.subject.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectKind;
import com.laminar.context.SubjectRole;
import com.laminar.subject.application.InvitationService;
import com.laminar.testsupport.WebTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 초대 표면 HTTP 게이트 (Q3) — 목록/발급/회수는 ADMIN+ 전용.
 *
 * <p>특히 대기 초대 목록은 초대 대상 이메일·역할을 노출하므로 MEMBER 차단을 못박는다(리뷰 5차 지적). 토큰 발급·수락 흐름 자체는 IT가 담당 — 여기서는 컨트롤러
 * 역할 게이트와 envelope 변환만.
 */
class InvitationControllerTest {

  private static final UUID SUBJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  private InvitationService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(InvitationService.class);
    mvc = WebTestSupport.mvc(new InvitationController(service));
  }

  @AfterEach
  void tearDown() {
    SubjectContextHolder.clear();
  }

  private static void contextWithRole(SubjectRole role) {
    SubjectContextHolder.set(
        SubjectContext.personal(
            SUBJECT_ID, WebTestSupport.PRINCIPAL.userId(), role, SubjectKind.LAB));
  }

  @Test
  void 초대_목록은_ADMIN이면_200() throws Exception {
    contextWithRole(SubjectRole.ADMIN);
    given(service.listPendingForCurrentSubject()).willReturn(List.of());

    mvc.perform(get("/api/subjects/current/invitations").principal(WebTestSupport.auth()))
        .andExpect(status().isOk());
  }

  @Test
  void 초대_목록은_MEMBER면_403_서비스_미호출() throws Exception {
    contextWithRole(SubjectRole.MEMBER);

    mvc.perform(get("/api/subjects/current/invitations").principal(WebTestSupport.auth()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("초대 목록은 관리자만 조회할 수 있습니다"));

    verifyNoInteractions(service);
  }

  @Test
  void 초대_발급은_MEMBER면_403_서비스_미호출() throws Exception {
    contextWithRole(SubjectRole.MEMBER);

    mvc.perform(
            post("/api/subjects/current/invitations")
                .principal(WebTestSupport.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@test.local\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }
}

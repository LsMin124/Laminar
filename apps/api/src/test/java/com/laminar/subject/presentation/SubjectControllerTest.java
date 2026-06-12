package com.laminar.subject.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.laminar.context.SubjectKind;
import com.laminar.error.ForbiddenException;
import com.laminar.subject.application.SubjectService;
import com.laminar.subject.domain.SubjectEntity;
import com.laminar.testsupport.WebTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** /api/subjects 매핑·검증·상태코드 (R2²) — LAB 승격 표면 포함. */
class SubjectControllerTest {

  private SubjectService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(SubjectService.class);
    mvc = WebTestSupport.mvc(new SubjectController(service));
  }

  private static SubjectEntity subject(String name, SubjectKind kind) {
    SubjectEntity s = new SubjectEntity();
    s.setId(UUID.randomUUID());
    s.setName(name);
    s.setSlug("slug-1");
    s.setOwnerUserId(WebTestSupport.PRINCIPAL.userId());
    s.setKind(kind);
    return s;
  }

  @Test
  void 주제_생성은_200과_kind_PERSONAL을_반환한다() throws Exception {
    given(service.create(any(), any(), any(), any()))
        .willReturn(subject("새 주제", SubjectKind.PERSONAL));

    mvc.perform(
            post("/api/subjects")
                .principal(WebTestSupport.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"새 주제\",\"slug\":\"slug-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("새 주제"))
        .andExpect(jsonPath("$.kind").value("PERSONAL"));
  }

  @Test
  void 주제_생성_name_누락은_400() throws Exception {
    mvc.perform(
            post("/api/subjects")
                .principal(WebTestSupport.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"slug-1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("name")));
  }

  @Test
  void LAB_승격은_200과_kind_LAB을_반환한다() throws Exception {
    given(service.promoteCurrentToLab()).willReturn(subject("연구실", SubjectKind.LAB));

    mvc.perform(post("/api/subjects/current/promote-to-lab"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kind").value("LAB"));
  }

  @Test
  void LAB_승격_OWNER_아니면_403_envelope() throws Exception {
    // 서비스 가드의 ForbiddenException — 큐레이트된 메시지가 그대로 노출되는 계약(DX-5).
    given(service.promoteCurrentToLab()).willThrow(new ForbiddenException("주제 승격은 소유자만 가능합니다"));

    mvc.perform(post("/api/subjects/current/promote-to-lab"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("주제 승격은 소유자만 가능합니다"))
        .andExpect(jsonPath("$.path").value("/api/subjects/current/promote-to-lab"));
  }

  @Test
  void 현재_주제_삭제는_204_위임() throws Exception {
    mvc.perform(delete("/api/subjects/current").principal(WebTestSupport.auth()))
        .andExpect(status().isNoContent());

    verify(service).deleteCurrent(WebTestSupport.PRINCIPAL.userId());
  }
}

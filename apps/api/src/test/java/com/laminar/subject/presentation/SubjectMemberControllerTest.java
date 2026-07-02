package com.laminar.subject.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectKind;
import com.laminar.context.SubjectRole;
import com.laminar.subject.application.SubjectMemberService;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.testsupport.WebTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /api/subjects/current/members — §1.3 매트릭스의 컨트롤러 측 게이트(역할 변경=OWNER, 제거=ADMIN+).
 *
 * <p>서비스 측 세부 가드(ADMIN은 MEMBER만 제거 등)는 IT 담당 — 여기서는 컨트롤러가 SubjectContext 역할로 끊는 분기만.
 */
class SubjectMemberControllerTest {

  private static final UUID SUBJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private SubjectMemberService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(SubjectMemberService.class);
    mvc = WebTestSupport.mvc(new SubjectMemberController(service));
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
  void 역할_변경은_OWNER면_200() throws Exception {
    contextWithRole(SubjectRole.OWNER);
    UUID targetId = UUID.randomUUID();
    SubjectMemberEntity updated = mock(SubjectMemberEntity.class, RETURNS_DEEP_STUBS);
    given(updated.getId().getSubjectId()).willReturn(SUBJECT_ID);
    given(updated.getId().getUserId()).willReturn(targetId);
    given(updated.getRole()).willReturn(SubjectRole.ADMIN);
    given(service.updateRole(targetId, SubjectRole.ADMIN, WebTestSupport.PRINCIPAL.userId()))
        .willReturn(updated);

    mvc.perform(
            patch("/api/subjects/current/members/{userId}/role", targetId)
                .principal(WebTestSupport.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.userId").value(targetId.toString()));
  }

  @Test
  void 역할_변경은_ADMIN이어도_403_서비스_미호출() throws Exception {
    // §1.3: ADMIN 임명/해임 포함 역할 변경은 OWNER 전용 — ADMIN도 차단.
    contextWithRole(SubjectRole.ADMIN);

    mvc.perform(
            patch("/api/subjects/current/members/{userId}/role", UUID.randomUUID())
                .principal(WebTestSupport.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MEMBER\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("역할 변경은 OWNER만 가능합니다"));

    verifyNoInteractions(service);
  }

  @Test
  void 알수없는_역할_문자열은_400() throws Exception {
    contextWithRole(SubjectRole.OWNER);

    mvc.perform(
            patch("/api/subjects/current/members/{userId}/role", UUID.randomUUID())
                .principal(WebTestSupport.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"SUPERVISOR\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("요청 본문이 올바르지 않습니다"));
  }

  @Test
  void 멤버_제거는_ADMIN이면_204_위임() throws Exception {
    contextWithRole(SubjectRole.ADMIN);
    UUID targetId = UUID.randomUUID();

    mvc.perform(
            delete("/api/subjects/current/members/{userId}", targetId)
                .principal(WebTestSupport.auth()))
        .andExpect(status().isNoContent());

    verify(service).removeMember(targetId, WebTestSupport.PRINCIPAL.userId());
  }

  @Test
  void 멤버_제거는_MEMBER면_403_서비스_미호출() throws Exception {
    contextWithRole(SubjectRole.MEMBER);

    mvc.perform(
            delete("/api/subjects/current/members/{userId}", UUID.randomUUID())
                .principal(WebTestSupport.auth()))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void 컨텍스트_미설정이면_역할_변경은_403() throws Exception {
    // SubjectContextHolder.require()의 IllegalStateException → 블랭킷 403 (fail-closed).
    mvc.perform(
            patch("/api/subjects/current/members/{userId}/role", UUID.randomUUID())
                .principal(WebTestSupport.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MEMBER\"}"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }
}

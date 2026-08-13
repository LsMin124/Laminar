package com.laminar.whiteboard.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.laminar.testsupport.WebTestSupport;
import com.laminar.whiteboard.application.WhiteboardEdgeService;
import com.laminar.whiteboard.domain.WhiteboardEdgeEntity;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** /api/whiteboard-edges 매핑·검증·상태코드 (R2² standalone MockMvc). */
class WhiteboardEdgeControllerTest {

  private WhiteboardEdgeService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(WhiteboardEdgeService.class);
    mvc = WebTestSupport.mvc(new WhiteboardEdgeController(service));
  }

  @Test
  void 엣지_생성_fromNodeId_누락은_400() throws Exception {
    mvc.perform(
            post("/api/whiteboard-edges")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toNodeId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("fromNodeId")));
  }

  @Test
  void 엣지_라벨_수정은_200과_label을_반환한다() throws Exception {
    UUID edgeId = UUID.randomUUID();
    WhiteboardEdgeEntity updated = new WhiteboardEdgeEntity();
    updated.setId(edgeId);
    updated.setLabel("의존");
    given(service.update(eq(edgeId), eq("의존"))).willReturn(updated);

    mvc.perform(
            patch("/api/whiteboard-edges/{id}", edgeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"의존\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label").value("의존"));
  }

  @Test
  void 엣지_restore는_200과_엣지를_반환한다() throws Exception {
    UUID edgeId = UUID.randomUUID();
    WhiteboardEdgeEntity edge = new WhiteboardEdgeEntity();
    edge.setId(edgeId);
    given(service.restore(edgeId)).willReturn(edge);

    mvc.perform(post("/api/whiteboard-edges/{id}/restore", edgeId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(edgeId.toString()));
  }
}

package com.laminar.whiteboard.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.laminar.testsupport.WebTestSupport;
import com.laminar.whiteboard.application.WhiteboardNodeService;
import com.laminar.whiteboard.domain.WhiteboardNodeEntity;
import com.laminar.whiteboard.domain.WhiteboardNodeKind;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** /api/whiteboard-nodes 매핑·검증·상태코드 (R2² standalone MockMvc). */
class WhiteboardNodeControllerTest {

  private WhiteboardNodeService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(WhiteboardNodeService.class);
    mvc = WebTestSupport.mvc(new WhiteboardNodeController(service));
  }

  @Test
  void 노드_생성은_200과_kind_좌표를_반환한다() throws Exception {
    WhiteboardNodeEntity node = new WhiteboardNodeEntity();
    node.setId(UUID.randomUUID());
    node.setKind(WhiteboardNodeKind.MD);
    node.setX(120.5);
    node.setY(-40.0);
    given(service.create(any())).willReturn(node);

    mvc.perform(
            post("/api/whiteboard-nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tabId\":\""
                        + UUID.randomUUID()
                        + "\",\"kind\":\"MD\",\"x\":120.5,\"y\":-40.0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kind").value("MD"))
        .andExpect(jsonPath("$.x").value(120.5))
        .andExpect(jsonPath("$.y").value(-40.0));
  }

  @Test
  void 노드_생성_kind_누락은_400() throws Exception {
    mvc.perform(
            post("/api/whiteboard-nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tabId\":\"" + UUID.randomUUID() + "\",\"x\":1.0,\"y\":2.0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("kind")));
  }

  @Test
  void 노드_생성_좌표_누락은_400() throws Exception {
    mvc.perform(
            post("/api/whiteboard-nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tabId\":\"" + UUID.randomUUID() + "\",\"kind\":\"MD\",\"y\":2.0}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 스티키_노드_생성은_kind_STICKY를_반환한다() throws Exception {
    WhiteboardNodeEntity node = new WhiteboardNodeEntity();
    node.setId(UUID.randomUUID());
    node.setKind(WhiteboardNodeKind.STICKY);
    node.setX(0.0);
    node.setY(0.0);
    given(service.create(any())).willReturn(node);

    mvc.perform(
            post("/api/whiteboard-nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tabId\":\""
                        + UUID.randomUUID()
                        + "\",\"kind\":\"STICKY\",\"x\":0.0,\"y\":0.0,\"attrs\":{\"color\":\"amber\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kind").value("STICKY"));
  }

  @Test
  void 노드_restore는_200과_노드를_반환한다() throws Exception {
    UUID nodeId = UUID.randomUUID();
    WhiteboardNodeEntity node = new WhiteboardNodeEntity();
    node.setId(nodeId);
    node.setKind(WhiteboardNodeKind.MD);
    node.setX(1.0);
    node.setY(2.0);
    given(service.restore(nodeId)).willReturn(node);

    mvc.perform(post("/api/whiteboard-nodes/{id}/restore", nodeId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(nodeId.toString()));
  }
}

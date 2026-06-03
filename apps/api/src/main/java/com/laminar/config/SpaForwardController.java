package com.laminar.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA fallback (Task 1.6.5).
 *
 * <p>비-API GET 요청 중 확장자 없는 경로는 {@code /index.html}로 forward → React Router가 처리. {@code
 * /api/**}·{@code /actuator/**} 같은 backend 경로는 더 구체적인 컨트롤러가 먼저 매칭되므로 영향 없음. 정적 파일(<code>.js</code>·
 * <code>.css</code> 등)은 확장자 매치로 제외 되어 Spring 기본 ResourceHandler가 처리.
 *
 * <p>Phase 5에서 정교화 예정 (auth-aware fallback, 미인증 시 /login 리디렉트 등).
 */
@Controller
public class SpaForwardController {

  /** 단일 세그먼트(<code>/board</code>) + 다중 세그먼트(<code>/workspace/abc/board/xyz</code>) 모두 매칭. */
  @GetMapping(value = {"/{path:[^\\.]*}", "/**/{path:[^\\.]*}"})
  public String spaFallback() {
    return "forward:/index.html";
  }
}

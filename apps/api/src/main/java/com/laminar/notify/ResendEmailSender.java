package com.laminar.notify;

import com.laminar.config.MailProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Resend(이메일 API) 발송기.
 *
 * <p>RESEND_API_KEY 미설정 시 발송을 생략하고 재설정 링크를 WARN 로그로 남긴다 — 오너가 {@code fly logs}로 링크를 받아 이메일 설정 전에도
 * 잠금을 풀 수 있게 하는 폴백. 발송 실패는 삼키고 로깅(요청 응답은 항상 동일 — 계정 enumeration 차단).
 */
@Component
public class ResendEmailSender {

  private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
  private static final String RESEND_URL = "https://api.resend.com/emails";

  private final MailProperties props;
  private final RestClient http = RestClient.create();

  public ResendEmailSender(MailProperties props) {
    this.props = props;
  }

  public void sendPasswordReset(String toEmail, String resetUrl) {
    String key = props.resendApiKey();
    if (key == null || key.isBlank()) {
      log.warn("[mail] RESEND_API_KEY 미설정 — 발송 생략. 비밀번호 재설정 링크(폴백): {} → {}", toEmail, resetUrl);
      return;
    }
    try {
      http.post()
          .uri(RESEND_URL)
          .header("Authorization", "Bearer " + key)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              Map.of(
                  "from",
                  props.from(),
                  "to",
                  List.of(toEmail),
                  "subject",
                  "Laminar 비밀번호 재설정",
                  "html",
                  "<p>비밀번호를 재설정하려면 아래 링크를 누르세요 (1시간 동안 유효).</p>"
                      + "<p><a href=\""
                      + resetUrl
                      + "\">비밀번호 재설정</a></p>"
                      + "<p>요청하지 않았다면 이 메일을 무시하세요.</p>"))
          .retrieve()
          .toBodilessEntity();
      log.info("[mail] 비밀번호 재설정 메일 발송 완료: {}", toEmail);
    } catch (Exception e) {
      log.error("[mail] 비밀번호 재설정 메일 발송 실패: {} ({})", toEmail, e.getMessage());
    }
  }
}

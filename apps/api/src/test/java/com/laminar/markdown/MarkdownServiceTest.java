package com.laminar.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** MarkdownService — XSS 방지 + 정상 마크다운 렌더링 검증 (DB 무관 unit). */
class MarkdownServiceTest {

  private final MarkdownService service = new MarkdownService();

  @Test
  void null_and_empty_return_empty_string() {
    assertThat(service.render(null)).isEmpty();
    assertThat(service.render("")).isEmpty();
  }

  @Test
  void plain_markdown_renders_basic_html() {
    String html = service.render("# Heading\n\nHello **world**.");

    assertThat(html).contains("<h1>", "Heading", "<strong>", "world");
  }

  @Test
  void gfm_tables_extension_renders_table() {
    String md =
        """
                | Col A | Col B |
                |-------|-------|
                | 1     | 2     |
                """;

    String html = service.render(md);

    assertThat(html).contains("<table", "<thead", "<tbody", "<th", "<td");
  }

  @Test
  void script_tag_is_stripped() {
    String html = service.render("Hello <script>alert('xss')</script> world");

    assertThat(html).doesNotContain("<script", "alert");
  }

  @Test
  void event_handlers_are_stripped() {
    String html = service.render("<a href=\"x\" onclick=\"steal()\">link</a>");

    assertThat(html).doesNotContain("onclick", "steal");
  }

  @Test
  void javascript_url_is_stripped() {
    String html = service.render("[click](javascript:alert('xss'))");

    assertThat(html).doesNotContain("javascript:", "alert");
  }

  @Test
  void oversized_input_throws() {
    MarkdownService bounded = new MarkdownService(100);
    String tooLong = "x".repeat(200);

    assertThatThrownBy(() -> bounded.render(tooLong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds");
  }
}

package com.laminar.markdown;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Service;

/**
 * 마크다운 → 안전한 HTML.
 *
 * <p>파이프라인: 1. commonmark + gfm-tables 확장 → unsafe HTML 2. OWASP Java HTML Sanitizer (Formatting +
 * Links + Blocks + Tables + Images) 3. 250KB byte 한도 (attrs.long_body_override 옵트인 추가 검증은 서비스 레이어
 * 별도)
 *
 * <p>Spec §11.4: XSS 방지 + 사용자 마크다운 신뢰 0.
 */
@Service
public class MarkdownService {

  static final int DEFAULT_MAX_BYTES = 250 * 1024;

  private final Parser parser;
  private final HtmlRenderer renderer;
  private final PolicyFactory sanitizer;
  private final int maxBytes;

  public MarkdownService() {
    this(DEFAULT_MAX_BYTES);
  }

  MarkdownService(int maxBytes) {
    List<Extension> extensions = List.of(TablesExtension.create());
    this.parser = Parser.builder().extensions(extensions).build();
    this.renderer = HtmlRenderer.builder().extensions(extensions).build();
    this.sanitizer =
        Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.IMAGES);
    this.maxBytes = maxBytes;
  }

  /** 렌더링 — null/empty는 빈 문자열. 한도 초과 시 IllegalArgumentException. */
  public String render(String markdown) {
    if (markdown == null || markdown.isEmpty()) {
      return "";
    }
    int byteLength = markdown.getBytes(StandardCharsets.UTF_8).length;
    if (byteLength > maxBytes) {
      throw new IllegalArgumentException(
          "markdown body exceeds " + maxBytes + " bytes (got " + byteLength + ")");
    }
    Node parsed = parser.parse(markdown);
    String unsafeHtml = renderer.render(parsed);
    return sanitizer.sanitize(unsafeHtml);
  }
}

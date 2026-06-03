package com.laminar.context;

import com.laminar.security.LaminarPrincipal;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.repository.SubjectMemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SecurityContext + 워크스페이스 헤더 → SubjectContext 도출 → ThreadLocal set.
 *
 * <p>Spring Security FilterChain (default Order=-100) 이후 동작 (Order=50). JwtAuthenticationFilter가
 * SecurityContext에 LaminarPrincipal을 채운 다음 본 필터가 워크스페이스 진입 여부를 결정.
 *
 * <p>scope 결정 매트릭스: - 비인증 + subjectId 없음 → SYSTEM (health/login 등) - 인증 + subjectId 없음 → SYSTEM
 * (워크스페이스 미선택, /api/subjects 등 글로벌) - 인증 + subjectId 있고 활성 멤버 → PERSONAL (subjectId·userId·role) -
 * 인증 + subjectId 있는데 멤버 아님 → 403 Forbidden (격리 위반 시도) - 비인증 + subjectId 있음 → 401 Unauthorized
 */
@Component
@Order(50)
public class SubjectContextRequestFilter extends OncePerRequestFilter {

  // 활성 주제(Subject) 선택 헤더 — 프론트가 송신, 본 필터가 SubjectContext 도출에 사용.
  private static final String HEADER_SUBJECT_ID = "X-Laminar-Subject-Id";

  private final SubjectMemberRepository memberRepo;

  public SubjectContextRequestFilter(SubjectMemberRepository memberRepo) {
    this.memberRepo = memberRepo;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    UUID subjectId = parseUuid(request.getHeader(HEADER_SUBJECT_ID));
    Optional<LaminarPrincipal> maybePrincipal = currentPrincipal();

    if (subjectId != null && maybePrincipal.isEmpty()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "subject requires authentication");
      return;
    }

    SubjectContext context;
    if (subjectId == null) {
      context = SubjectContext.system();
    } else {
      UUID userId = maybePrincipal.get().userId();
      Optional<SubjectMemberEntity> member =
          memberRepo.findByIdSubjectIdAndIdUserIdAndRemovedAtIsNull(subjectId, userId);
      if (member.isEmpty()) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "not a member of subject");
        return;
      }
      context = SubjectContext.personal(subjectId, userId, member.get().getRole());
    }

    SubjectContextHolder.set(context);
    try {
      chain.doFilter(request, response);
    } finally {
      SubjectContextHolder.clear();
    }
  }

  private Optional<LaminarPrincipal> currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) return Optional.empty();
    Object principal = auth.getPrincipal();
    return principal instanceof LaminarPrincipal lp ? Optional.of(lp) : Optional.empty();
  }

  private UUID parseUuid(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}

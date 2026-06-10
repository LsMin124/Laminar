package com.laminar.common.repository;

import com.laminar.common.domain.PersonalBaseEntity;
import com.laminar.common.domain.SoftDeletable;
import com.laminar.context.SubjectContext;
import com.laminar.error.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * PERSONAL 스코프 소유 조회 믹스인 (DX-1②+DX-15) — "PK 조회 + soft-delete 제외 + 소유권 검증 + 부재=404"를 한 호출로.
 *
 * <p>과거 18개 서비스가 {@code findById(..).filter(deletedAt==null).filter(ctx.ownsPersonal(..))} 체인을 손으로
 * 복제했고, 한 filter를 빠뜨려도 어디서도 안 잡혔다(격리 누락의 구조적 확률). 이 믹스인이 단일 정본이며, 격리 정책 변경(예: archived 처리)도 여기 한
 * 곳에서 끝난다.
 *
 * <p>"없음"과 "남의 것"을 모두 {@link NotFoundException}(404)으로 동일하게 응답한다 — 존재 여부 노출(enumeration) 방지. 입력 검증
 * 실패(400)와는 의미가 다르다(DX-15: 과거 IllegalArgumentException→400으로 뭉개지던 것 교정).
 *
 * <p>OWNER_SCOPED(장비, ownsUser)·SUBJECT_SHARED(ownsShared) 엔티티는 소유 술어가 달라 이 믹스인 대상이 아니다.
 */
@NoRepositoryBean
public interface PersonalOwnedRepository<T extends PersonalBaseEntity & SoftDeletable>
    extends JpaRepository<T, UUID> {

  /** 활성(미삭제) + 현재 컨텍스트 소유 엔티티. 추가 조건을 더 걸어야 하는 호출부용 Optional 형. */
  default Optional<T> findOwnedActive(UUID id, SubjectContext ctx) {
    return findById(id)
        .filter(e -> e.getDeletedAt() == null)
        .filter(e -> ctx.ownsPersonal(e.getSubjectId(), e.getUserId()));
  }

  /**
   * 활성 + 소유 엔티티, 없으면 {@link NotFoundException}(404).
   *
   * @param noun 메시지에 들어갈 리소스 명사(예: "card", "group") — "{noun} not found"
   */
  default T findOwnedActiveOrThrow(UUID id, SubjectContext ctx, String noun) {
    return findOwnedActive(id, ctx).orElseThrow(() -> new NotFoundException(noun + " not found"));
  }
}

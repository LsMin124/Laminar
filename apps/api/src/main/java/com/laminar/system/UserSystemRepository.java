package com.laminar.system;

import com.laminar.user.domain.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 글로벌 lookup (시스템 컨텍스트 — 격리 우회).
 *
 * <p>인증·가입·세션 발급 시점에 사용. 일반 비즈니스 코드는 WorkspaceContext.userId() 경유로 조회 — 이 Repository 직접 사용은 보안 경계
 * (auth/identity service만).
 */
public interface UserSystemRepository extends JpaRepository<UserEntity, UUID>, SystemRepository {

  Optional<UserEntity> findByEmail(String email);

  Optional<UserEntity> findByEmailAndDeletedAtIsNull(String email);
}

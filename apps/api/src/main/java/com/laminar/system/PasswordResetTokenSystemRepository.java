package com.laminar.system;

import com.laminar.user.domain.PasswordResetTokenEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 비밀번호 재설정 토큰 시스템 Repository — 사용자 글로벌 자원이라 SubjectContext 격리(@Filter) 미적용(SystemRepository 마커).
 */
public interface PasswordResetTokenSystemRepository
    extends JpaRepository<PasswordResetTokenEntity, UUID>, SystemRepository {

  Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

  long deleteByExpiresAtBefore(OffsetDateTime cutoff);
}

package com.laminar.system;

import com.laminar.user.SessionEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Auth.js DB 세션 어댑터용 시스템 Repository.
 *
 * <p>멤버 제거 시 즉시 revoke 위해 DB 세션 사용 (Spec §5.4). 만료 정리 cron이 deleteByExpiresAtBefore 사용.
 */
public interface SessionSystemRepository
    extends JpaRepository<SessionEntity, UUID>, SystemRepository {

  Optional<SessionEntity> findBySessionToken(String sessionToken);

  List<SessionEntity> findByUserId(UUID userId);

  long deleteByExpiresAtBefore(OffsetDateTime cutoff);
}

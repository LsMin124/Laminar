package com.laminar.system;

import com.laminar.outbox.EmailOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * email_outbox 시스템 Repository — workspace 무관 (워크스페이스 무관 발송이 다수).
 *
 * INSERT는 모든 서비스에서 호출 (트랜잭션 내), flush cron은 sent_at IS NULL 폴링.
 */
public interface EmailOutboxSystemRepository
        extends JpaRepository<EmailOutboxEntity, UUID>, SystemRepository {

    List<EmailOutboxEntity> findBySentAtIsNullOrderByCreatedAtAsc();
}

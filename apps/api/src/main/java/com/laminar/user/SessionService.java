package com.laminar.user;

import com.laminar.system.SessionSystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * 세션 발급·revoke·정리.
 *
 * 토큰: 256-bit SecureRandom + base64url. sessions 테이블이 SOR (멤버 제거 즉시 revoke).
 * Cookie 발급은 AuthController 책임 — 본 서비스는 토큰만 반환.
 */
@Service
public class SessionService {

    static final int DEFAULT_TTL_DAYS = 28;
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SessionSystemRepository sessionRepo;

    public SessionService(SessionSystemRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @Transactional
    public SessionEntity issue(UUID userId) {
        return issue(userId, DEFAULT_TTL_DAYS);
    }

    @Transactional
    public SessionEntity issue(UUID userId, int ttlDays) {
        SessionEntity session = new SessionEntity();
        session.setUserId(userId);
        session.setSessionToken(generateToken());
        session.setExpiresAt(OffsetDateTime.now().plus(ttlDays, ChronoUnit.DAYS));
        return sessionRepo.save(session);
    }

    @Transactional
    public void revoke(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) return;
        sessionRepo.findBySessionToken(sessionToken).ifPresent(sessionRepo::delete);
    }

    /**
     * 만료된 세션 정리 — cleanup cron 매시 호출.
     */
    @Transactional
    public long purgeExpired() {
        return sessionRepo.deleteByExpiresAtBefore(OffsetDateTime.now());
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

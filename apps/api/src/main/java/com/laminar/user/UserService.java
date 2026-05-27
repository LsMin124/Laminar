package com.laminar.user;

import com.laminar.system.UserSystemRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 도메인 서비스 (가입·인증).
 *
 * 시스템 컨텍스트 (UserSystemRepository) 사용 — 사용자 자체는 글로벌 자원. 워크스페이스 진입 후
 * WorkspaceContext.userId()로 격리.
 */
@Service
public class UserService {

    private final UserSystemRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserSystemRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 이메일 가입 — 중복 시 IllegalStateException. BCrypt 12 round 해시.
     * emailVerifiedAt=null (검증 토큰은 Phase 4.5에서 email_outbox INSERT).
     */
    @Transactional
    public UserEntity signup(String email, String rawPassword, String displayName) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepo.findByEmailAndDeletedAtIsNull(normalizedEmail).isPresent()) {
            throw new IllegalStateException("email already registered");
        }
        UserEntity user = new UserEntity();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        return userRepo.save(user);
    }

    /**
     * 이메일·비밀번호 검증 — soft delete된 사용자·해시 미설정·불일치 시 빈 Optional.
     */
    @Transactional(readOnly = true)
    public Optional<UserEntity> verifyCredentials(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        return userRepo.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .filter(u -> u.getPasswordHash() != null)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()));
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findActive(UUID userId) {
        return userRepo.findById(userId).filter(u -> u.getDeletedAt() == null);
    }
}

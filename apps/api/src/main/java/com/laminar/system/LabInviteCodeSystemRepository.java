package com.laminar.system;

import com.laminar.subject.domain.LabInviteCodeEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * LAB 초대코드의 시스템(컨텍스트 밖) 조회 — 가입 신청자는 아직 비멤버라 lab 컨텍스트로 진입할 수 없다(SYSTEM scope, 헤더 없이 호출). 세션·비밀번호
 * 재설정 토큰과 동일한 "가입 전 cross-tenant 진입점" 패턴.
 */
public interface LabInviteCodeSystemRepository
    extends JpaRepository<LabInviteCodeEntity, UUID>, SystemRepository {

  Optional<LabInviteCodeEntity> findByCodeAndRevokedAtIsNull(String code);
}

package com.laminar.subject.repository;

import com.laminar.subject.domain.SubjectEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Subject Repository (subject-shared scope — self-filter id = :ctxSubjectId).
 *
 * <p>HibernateFilterActivator가 subjectSharedFilter를 enable하면 자동으로 현재 컨텍스트 워크스페이스만 노출. 워크스페이스 진입 전
 * (SYSTEM scope)에는 미필터.
 */
public interface SubjectRepository extends JpaRepository<SubjectEntity, UUID> {

  Optional<SubjectEntity> findBySlug(String slug);

  Optional<SubjectEntity> findBySlugAndDeletedAtIsNull(String slug);
}

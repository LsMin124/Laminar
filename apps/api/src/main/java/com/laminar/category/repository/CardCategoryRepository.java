package com.laminar.category.repository;

import com.laminar.category.domain.CardCategoryEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 카드 카테고리 Repository — subjectSharedFilter 대상이라 findAll 등 list 쿼리는 현재 주제로 격리된다. findById(PK)는 필터
 * 미적용이므로 서비스에서 subjectId 소유 검증.
 */
public interface CardCategoryRepository extends JpaRepository<CardCategoryEntity, UUID> {}

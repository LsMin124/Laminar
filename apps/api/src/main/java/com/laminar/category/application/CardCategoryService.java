package com.laminar.category.application;

import com.laminar.category.domain.CardCategoryEntity;
import com.laminar.category.repository.CardCategoryRepository;
import com.laminar.context.SubjectContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 카테고리 도메인 서비스 — 현재 주제(SubjectContext) 기준 목록/생성/수정/삭제.
 *
 * <p>list는 subjectSharedFilter로 현재 주제만. find/update/delete by id는 PK 로드라 필터 미적용 → subjectId 소유 검증으로
 * 교차 주제 접근을 차단(소유 위반은 IllegalStateException→403, 미존재는 IllegalArgumentException→400).
 */
@Service
public class CardCategoryService {

  private final CardCategoryRepository repo;

  public CardCategoryService(CardCategoryRepository repo) {
    this.repo = repo;
  }

  @Transactional(readOnly = true)
  public List<CardCategoryEntity> list() {
    return repo.findAll();
  }

  @Transactional
  public CardCategoryEntity create(String name, String color) {
    CardCategoryEntity category = new CardCategoryEntity();
    category.setSubjectId(currentSubjectId());
    category.setName(name);
    category.setColor(color);
    return repo.save(category);
  }

  @Transactional
  public CardCategoryEntity update(UUID id, String name, String color) {
    CardCategoryEntity category = requireOwned(id);
    if (name != null && !name.isBlank()) {
      category.setName(name);
    }
    if (color != null) {
      category.setColor(color);
    }
    return repo.save(category);
  }

  @Transactional
  public void delete(UUID id) {
    repo.delete(requireOwned(id));
  }

  private CardCategoryEntity requireOwned(UUID id) {
    CardCategoryEntity category =
        repo.findById(id).orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다."));
    if (!category.getSubjectId().equals(currentSubjectId())) {
      throw new IllegalStateException("category not owned by current subject");
    }
    return category;
  }

  private UUID currentSubjectId() {
    return SubjectContextHolder.require().subjectId();
  }
}

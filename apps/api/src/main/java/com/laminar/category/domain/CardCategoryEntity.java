package com.laminar.category.domain;

import com.laminar.common.domain.SubjectScopedBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/** 카드 카테고리 — 주제(subject) 단위 공유. subjectSharedFilter로 격리(현재 주제만). */
@Entity
@Table(name = "card_categories")
@Filter(name = "subjectSharedFilter", condition = "subject_id = :ctxSubjectId")
@Getter
@Setter
public class CardCategoryEntity extends SubjectScopedBaseEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "color")
  private String color;
}

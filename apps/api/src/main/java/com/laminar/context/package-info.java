/**
 * Hibernate @FilterDef 패키지 레벨 선언.
 *
 * <p>personalFirstFilter — subject_id + user_id 이중 필터 (Personal-First 엔티티) subjectSharedFilter —
 * subject_id 단일 필터 (subject-shared 엔티티)
 *
 * <p>엔티티는 @Filter(name = ..., condition = ...)로 컬럼 조건 명시. HibernateFilterActivator가 트랜잭션 진입 시 파라미터
 * 바인딩 + enable.
 */
@FilterDef(
    name = "personalFirstFilter",
    parameters = {
      @ParamDef(name = "ctxSubjectId", type = UUID.class),
      @ParamDef(name = "ctxUserId", type = UUID.class)
    })
@FilterDef(
    name = "subjectSharedFilter",
    parameters = @ParamDef(name = "ctxSubjectId", type = UUID.class))
@FilterDef(
    name = "ownerScopedFilter",
    parameters = @ParamDef(name = "ctxUserId", type = UUID.class))
package com.laminar.context;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

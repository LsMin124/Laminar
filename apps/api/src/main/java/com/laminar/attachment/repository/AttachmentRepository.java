package com.laminar.attachment.repository;

import com.laminar.attachment.domain.AttachmentEntity;
import com.laminar.attachment.domain.AttachmentParentType;
import com.laminar.common.repository.PersonalOwnedRepository;
import java.util.List;
import java.util.UUID;

/** 첨부 Repository — Personal-First (@Filter 자동). */
public interface AttachmentRepository extends PersonalOwnedRepository<AttachmentEntity> {

  List<AttachmentEntity> findByParentTypeAndParentIdAndDeletedAtIsNull(
      AttachmentParentType parentType, UUID parentId);
}

package com.laminar.attachment.repository;

import com.laminar.attachment.domain.AttachmentEntity;
import com.laminar.attachment.domain.AttachmentParentType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 첨부 Repository — Personal-First (@Filter 자동). */
public interface AttachmentRepository extends JpaRepository<AttachmentEntity, UUID> {

  List<AttachmentEntity> findByParentTypeAndParentIdAndDeletedAtIsNull(
      AttachmentParentType parentType, UUID parentId);
}

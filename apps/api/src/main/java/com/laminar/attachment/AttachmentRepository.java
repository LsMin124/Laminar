package com.laminar.attachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 첨부 Repository — Personal-First (@Filter 자동).
 */
public interface AttachmentRepository extends JpaRepository<AttachmentEntity, UUID> {

    List<AttachmentEntity> findByParentTypeAndParentIdAndDeletedAtIsNull(
            AttachmentParentType parentType, UUID parentId);
}

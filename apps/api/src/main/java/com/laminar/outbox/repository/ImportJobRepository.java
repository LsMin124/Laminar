package com.laminar.outbox.repository;

import com.laminar.outbox.domain.ImportJobEntity;
import com.laminar.outbox.domain.ImportJobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** ImportJob Repository — Personal-First (@Filter 자동). */
public interface ImportJobRepository extends JpaRepository<ImportJobEntity, UUID> {

  List<ImportJobEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();

  List<ImportJobEntity> findByStatusAndDeletedAtIsNull(ImportJobStatus status);

  Optional<ImportJobEntity> findByImportTokenAndDeletedAtIsNull(String importToken);
}

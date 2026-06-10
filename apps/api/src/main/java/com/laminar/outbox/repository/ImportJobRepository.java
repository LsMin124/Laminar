package com.laminar.outbox.repository;

import com.laminar.common.repository.PersonalOwnedRepository;
import com.laminar.outbox.domain.ImportJobEntity;
import com.laminar.outbox.domain.ImportJobStatus;
import java.util.List;
import java.util.Optional;

/** ImportJob Repository — Personal-First (@Filter 자동). */
public interface ImportJobRepository extends PersonalOwnedRepository<ImportJobEntity> {

  List<ImportJobEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();

  List<ImportJobEntity> findByStatusAndDeletedAtIsNull(ImportJobStatus status);

  Optional<ImportJobEntity> findByImportTokenAndDeletedAtIsNull(String importToken);
}

package com.laminar.perpetual;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 영구노트 버전 Repository — Personal-First. 노트당 version_number 자동 부여, is_current_diff 정확히 1건 (DB partial
 * unique).
 */
public interface PerpetualVersionRepository extends JpaRepository<PerpetualVersionEntity, UUID> {

  List<PerpetualVersionEntity> findByPerpetualNoteIdAndDeletedAtIsNullOrderByVersionNumberDesc(
      UUID perpetualNoteId);

  Optional<PerpetualVersionEntity>
      findFirstByPerpetualNoteIdAndDeletedAtIsNullOrderByVersionNumberDesc(UUID perpetualNoteId);

  Optional<PerpetualVersionEntity> findByPerpetualNoteIdAndCurrentDiffIsTrueAndDeletedAtIsNull(
      UUID perpetualNoteId);

  Optional<PerpetualVersionEntity> findByCardId(UUID cardId);
}

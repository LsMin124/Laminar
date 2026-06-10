package com.laminar.samplemanager.repository;

import com.laminar.common.repository.PersonalOwnedRepository;
import com.laminar.samplemanager.domain.SampleManagerLinkEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SM 링크 Repository — Personal-First (@Filter 자동). (card_id, sample_id, step_id) unique 활용한 멱등
 * import.
 */
public interface SampleManagerLinkRepository
    extends PersonalOwnedRepository<SampleManagerLinkEntity> {

  Optional<SampleManagerLinkEntity> findByCardIdAndSampleIdAndStepIdAndDeletedAtIsNull(
      UUID cardId, String sampleId, String stepId);

  List<SampleManagerLinkEntity> findByCardIdAndDeletedAtIsNull(UUID cardId);
}

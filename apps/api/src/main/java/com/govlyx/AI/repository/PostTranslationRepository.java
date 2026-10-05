package com.govlyx.AI.repository;

import com.govlyx.AI.model.PostTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostTranslationRepository extends JpaRepository<PostTranslation, Long> {
    
    List<PostTranslation> findByReferenceIdInAndReferenceTypeAndTargetLanguage(
            List<Long> referenceIds, String referenceType, String targetLanguage);

    Optional<PostTranslation> findByReferenceIdAndReferenceTypeAndTargetLanguage(
            Long referenceId, String referenceType, String targetLanguage);
}

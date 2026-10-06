package com.cognologix.fpa.contracts.repository;

import com.cognologix.fpa.contracts.dto.TemplateDocumentMeta;
import com.cognologix.fpa.contracts.domain.ContractTemplateDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractTemplateDocumentRepository extends JpaRepository<ContractTemplateDocument, UUID> {

    Optional<ContractTemplateDocument> findFirstByTemplateIdOrderByVersionNumberDesc(UUID templateId);

    @Query("select coalesce(max(d.versionNumber), 0) from ContractTemplateDocument d where d.templateId = :templateId")
    int maxVersionNumber(UUID templateId);

    @Query("""
            select new com.cognologix.fpa.contracts.dto.TemplateDocumentMeta(
                d.id, d.templateId, d.versionNumber, d.filename, d.contentType,
                d.fileSizeBytes, d.uploadedAt, d.uploadedBy)
            from ContractTemplateDocument d
            where d.templateId in :templateIds
              and d.versionNumber = (
                  select max(d2.versionNumber) from ContractTemplateDocument d2
                  where d2.templateId = d.templateId
              )
            """)
    List<TemplateDocumentMeta> findLatestMeta(Collection<UUID> templateIds);
}

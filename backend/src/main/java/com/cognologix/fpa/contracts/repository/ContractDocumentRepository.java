package com.cognologix.fpa.contracts.repository;

import com.cognologix.fpa.contracts.dto.DocumentMeta;
import com.cognologix.fpa.contracts.domain.ContractDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContractDocumentRepository extends JpaRepository<ContractDocument, UUID> {

    @Query("""
            select new com.cognologix.fpa.contracts.dto.DocumentMeta(
                d.id, d.contractVersionId, d.documentType, d.filename, d.contentType,
                d.fileSizeBytes, d.uploadedAt, d.uploadedBy)
            from ContractDocument d
            where d.contractVersionId in :versionIds
            """)
    List<DocumentMeta> findMetaByVersionIds(Collection<UUID> versionIds);
}

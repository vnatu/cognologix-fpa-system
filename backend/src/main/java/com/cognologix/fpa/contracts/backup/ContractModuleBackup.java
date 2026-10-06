package com.cognologix.fpa.contracts.backup;

import com.cognologix.fpa.contracts.ContractNotificationType;
import com.cognologix.fpa.contracts.ContractStatus;
import com.cognologix.fpa.contracts.DocumentType;
import com.cognologix.fpa.contracts.PaperType;
import com.cognologix.fpa.contracts.VersionStatus;
import com.cognologix.fpa.contracts.domain.Contract;
import com.cognologix.fpa.contracts.domain.ContractDocument;
import com.cognologix.fpa.contracts.domain.ContractNotificationLog;
import com.cognologix.fpa.contracts.domain.ContractTemplate;
import com.cognologix.fpa.contracts.domain.ContractTemplateDocument;
import com.cognologix.fpa.contracts.domain.ContractType;
import com.cognologix.fpa.contracts.domain.ContractVersion;
import com.cognologix.fpa.contracts.repository.ContractDocumentRepository;
import com.cognologix.fpa.contracts.repository.ContractNotificationLogRepository;
import com.cognologix.fpa.contracts.repository.ContractRepository;
import com.cognologix.fpa.contracts.repository.ContractTemplateDocumentRepository;
import com.cognologix.fpa.contracts.repository.ContractTemplateRepository;
import com.cognologix.fpa.contracts.repository.ContractTypeRepository;
import com.cognologix.fpa.contracts.repository.ContractVersionRepository;
import com.cognologix.fpa.general.AppUser;
import com.cognologix.fpa.general.BackupSheet;
import com.cognologix.fpa.general.GeneralBadRequestException;
import com.cognologix.fpa.general.UserService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.cognologix.fpa.general.BackupGridHelper.cell;
import static com.cognologix.fpa.general.BackupGridHelper.parseBoolean;
import static com.cognologix.fpa.general.BackupGridHelper.parseDate;
import static com.cognologix.fpa.general.BackupGridHelper.parseDecimal;
import static com.cognologix.fpa.general.BackupGridHelper.parseInstant;
import static com.cognologix.fpa.general.BackupGridHelper.parseIntRequired;
import static com.cognologix.fpa.general.BackupGridHelper.requireCell;
import static com.cognologix.fpa.general.BackupGridHelper.row;
import static com.cognologix.fpa.general.BackupGridHelper.str;

/**
 * Contract tables in the system backup ZIP (ADR-067).
 * Document bytes are separate ZIP entries because a 20MB BYTEA cannot fit in an Excel cell.
 */
@Component
@RequiredArgsConstructor
public class ContractModuleBackup {

    public static final String TYPES = "contract_types.xlsx";
    public static final String CONTRACTS = "contracts.xlsx";
    public static final String VERSIONS = "contract_versions.xlsx";
    public static final String DOCUMENTS = "contract_documents.xlsx";
    public static final String TEMPLATES = "contract_templates.xlsx";
    public static final String TEMPLATE_DOCUMENTS = "contract_template_documents.xlsx";
    public static final String NOTIFICATION_LOG = "contract_notification_log.xlsx";
    public static final String BLOB_PREFIX = "contract_blobs/";
    public static final String TEMPLATE_BLOB_PREFIX = "contract_template_blobs/";

    private static final String[] TYPE_HEADERS = {
            "id", "type_code", "display_name", "description", "is_active", "created_at"
    };
    private static final String[] CONTRACT_HEADERS = {
            "id", "contract_number", "title", "contract_type_id", "paper_type", "customer_id", "party_name",
            "effective_date", "expiry_date", "is_evergreen", "status", "parent_contract_id", "contract_value",
            "billing_currency", "payment_terms", "reminder_days", "description", "owner_user_id",
            "created_at", "created_by", "updated_at", "updated_by", "owner_email"
    };
    private static final String[] VERSION_HEADERS = {
            "id", "contract_id", "version_number", "version_label", "status", "notes", "uploaded_at", "uploaded_by"
    };
    private static final String[] DOCUMENT_HEADERS = {
            "id", "contract_version_id", "document_type", "filename", "content_type", "file_size_bytes",
            "blob_entry", "uploaded_at", "uploaded_by"
    };
    private static final String[] TEMPLATE_HEADERS = {
            "id", "contract_type_id", "template_name", "description", "is_active", "created_at", "created_by"
    };
    private static final String[] TEMPLATE_DOCUMENT_HEADERS = {
            "id", "template_id", "version_number", "filename", "content_type", "file_size_bytes",
            "blob_entry", "uploaded_at", "uploaded_by"
    };
    private static final String[] LOG_HEADERS = {
            "id", "contract_id", "notification_type", "days_before_expiry", "sent_at", "recipients"
    };

    private final ContractTypeRepository contractTypeRepository;
    private final ContractRepository contractRepository;
    private final ContractVersionRepository contractVersionRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final ContractTemplateRepository contractTemplateRepository;
    private final ContractTemplateDocumentRepository contractTemplateDocumentRepository;
    private final ContractNotificationLogRepository contractNotificationLogRepository;
    private final UserService userService;
    private final EntityManager entityManager;

    public List<BackupSheet> exportSheets() {
        return List.of(
                typesSheet(),
                contractsSheet(),
                versionsSheet(),
                documentsSheet(),
                templatesSheet(),
                templateDocumentsSheet(),
                logSheet());
    }

    public Map<String, byte[]> exportBlobs() {
        Map<String, byte[]> blobs = new LinkedHashMap<>();
        for (ContractDocument document : contractDocumentRepository.findAll()) {
            blobs.put(BLOB_PREFIX + document.getId() + ".bin", document.getFileData());
        }
        for (ContractTemplateDocument document : contractTemplateDocumentRepository.findAll()) {
            blobs.put(TEMPLATE_BLOB_PREFIX + document.getId() + ".bin", document.getFileData());
        }
        return blobs;
    }

    @Transactional
    public void wipe() {
        contractNotificationLogRepository.deleteAllInBatch();
        contractDocumentRepository.deleteAllInBatch();
        contractVersionRepository.deleteAllInBatch();
        entityManager.createNativeQuery("UPDATE contract SET parent_contract_id = NULL").executeUpdate();
        entityManager.flush();
        entityManager.clear();
        contractRepository.deleteAllInBatch();
        contractTemplateDocumentRepository.deleteAllInBatch();
        contractTemplateRepository.deleteAllInBatch();
        contractTypeRepository.deleteAllInBatch();
        entityManager.flush();
        entityManager.clear();
    }

    @Transactional
    public Map<String, Integer> restore(Map<String, List<String[]>> rowsByFile, Map<String, byte[]> blobs) {
        Map<String, byte[]> files = blobs == null ? Map.of() : blobs;
        int types = restoreTypes(rowsByFile.getOrDefault(TYPES, List.of()));
        int contracts = restoreContracts(rowsByFile.getOrDefault(CONTRACTS, List.of()));
        int versions = restoreVersions(rowsByFile.getOrDefault(VERSIONS, List.of()));
        int documents = restoreDocuments(rowsByFile.getOrDefault(DOCUMENTS, List.of()), files);
        int templates = restoreTemplates(rowsByFile.getOrDefault(TEMPLATES, List.of()));
        int templateDocuments = restoreTemplateDocuments(
                rowsByFile.getOrDefault(TEMPLATE_DOCUMENTS, List.of()), files);
        int logs = restoreLogs(rowsByFile.getOrDefault(NOTIFICATION_LOG, List.of()));
        alignSequence();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(TYPES, types);
        counts.put(CONTRACTS, contracts);
        counts.put(VERSIONS, versions);
        counts.put(DOCUMENTS, documents);
        counts.put(TEMPLATES, templates);
        counts.put(TEMPLATE_DOCUMENTS, templateDocuments);
        counts.put(NOTIFICATION_LOG, logs);
        return counts;
    }

    private BackupSheet typesSheet() {
        List<String[]> rows = new ArrayList<>();
        for (ContractType type : contractTypeRepository.findAll()) {
            rows.add(row(
                    str(type.getId()), type.getTypeCode(), type.getDisplayName(), str(type.getDescription()),
                    String.valueOf(type.isActive()), str(type.getCreatedAt())));
        }
        return new BackupSheet(TYPES, TYPE_HEADERS, rows);
    }

    private BackupSheet contractsSheet() {
        List<String[]> rows = new ArrayList<>();
        for (Contract contract : contractRepository.findAll()) {
            rows.add(row(
                    str(contract.getId()),
                    contract.getContractNumber(),
                    contract.getTitle(),
                    str(contract.getContractType().getId()),
                    contract.getPaperType().name(),
                    str(contract.getCustomerId()),
                    str(contract.getPartyName()),
                    str(contract.getEffectiveDate()),
                    str(contract.getExpiryDate()),
                    String.valueOf(contract.isEvergreen()),
                    contract.getStatus().name(),
                    str(contract.getParentContractId()),
                    str(contract.getContractValue()),
                    str(contract.getBillingCurrency()),
                    str(contract.getPaymentTerms()),
                    reminderCsv(contract.getReminderDaysOverride()),
                    str(contract.getDescription()),
                    str(contract.getOwnerUserId()),
                    str(contract.getCreatedAt()),
                    contract.getCreatedBy(),
                    str(contract.getUpdatedAt()),
                    str(contract.getUpdatedBy()),
                    ownerEmail(contract.getOwnerUserId())));
        }
        return new BackupSheet(CONTRACTS, CONTRACT_HEADERS, rows);
    }

    private BackupSheet versionsSheet() {
        List<String[]> rows = new ArrayList<>();
        for (ContractVersion version : contractVersionRepository.findAll()) {
            rows.add(row(
                    str(version.getId()), str(version.getContractId()), str(version.getVersionNumber()),
                    version.getVersionLabel(), version.getStatus().name(), str(version.getNotes()),
                    str(version.getUploadedAt()), version.getUploadedBy()));
        }
        return new BackupSheet(VERSIONS, VERSION_HEADERS, rows);
    }

    private BackupSheet documentsSheet() {
        List<String[]> rows = new ArrayList<>();
        for (ContractDocument document : contractDocumentRepository.findAll()) {
            rows.add(row(
                    str(document.getId()),
                    str(document.getContractVersionId()),
                    document.getDocumentType().name(),
                    document.getFilename(),
                    document.getContentType(),
                    str(document.getFileSizeBytes()),
                    BLOB_PREFIX + document.getId() + ".bin",
                    str(document.getUploadedAt()),
                    document.getUploadedBy()));
        }
        return new BackupSheet(DOCUMENTS, DOCUMENT_HEADERS, rows);
    }

    private BackupSheet templatesSheet() {
        List<String[]> rows = new ArrayList<>();
        for (ContractTemplate template : contractTemplateRepository.findAll()) {
            rows.add(row(
                    str(template.getId()), str(template.getContractType().getId()), template.getTemplateName(),
                    str(template.getDescription()), String.valueOf(template.isActive()),
                    str(template.getCreatedAt()), template.getCreatedBy()));
        }
        return new BackupSheet(TEMPLATES, TEMPLATE_HEADERS, rows);
    }

    private BackupSheet templateDocumentsSheet() {
        List<String[]> rows = new ArrayList<>();
        for (ContractTemplateDocument document : contractTemplateDocumentRepository.findAll()) {
            rows.add(row(
                    str(document.getId()),
                    str(document.getTemplateId()),
                    str(document.getVersionNumber()),
                    document.getFilename(),
                    document.getContentType(),
                    str(document.getFileSizeBytes()),
                    TEMPLATE_BLOB_PREFIX + document.getId() + ".bin",
                    str(document.getUploadedAt()),
                    document.getUploadedBy()));
        }
        return new BackupSheet(TEMPLATE_DOCUMENTS, TEMPLATE_DOCUMENT_HEADERS, rows);
    }

    private BackupSheet logSheet() {
        List<String[]> rows = new ArrayList<>();
        for (ContractNotificationLog entry : contractNotificationLogRepository.findAll()) {
            rows.add(row(
                    str(entry.getId()), str(entry.getContractId()), entry.getNotificationType().name(),
                    str(entry.getDaysBeforeExpiry()), str(entry.getSentAt()), str(entry.getRecipients())));
        }
        return new BackupSheet(NOTIFICATION_LOG, LOG_HEADERS, rows);
    }

    private int restoreTypes(List<String[]> rows) {
        int count = 0;
        for (String[] cells : rows) {
            var type = new ContractType();
            type.setId(uuid(cells, 0, "id"));
            type.setTypeCode(requireCell(cells, 1, "type_code"));
            type.setDisplayName(requireCell(cells, 2, "display_name"));
            type.setDescription(cell(cells, 3));
            type.setActive(parseBoolean(cell(cells, 4)));
            var created = parseInstant(cell(cells, 5), "created_at");
            type.setCreatedAt(created == null ? java.time.Instant.now() : created);
            contractTypeRepository.save(type);
            count++;
        }
        contractTypeRepository.flush();
        return count;
    }

    private int restoreContracts(List<String[]> rows) {
        List<UUID> ids = new ArrayList<>();
        List<UUID> parents = new ArrayList<>();
        for (String[] cells : rows) {
            var contract = new Contract();
            UUID id = uuid(cells, 0, "id");
            contract.setId(id);
            contract.setContractNumber(requireCell(cells, 1, "contract_number"));
            contract.setTitle(requireCell(cells, 2, "title"));
            contract.setContractType(contractTypeRepository.findById(uuid(cells, 3, "contract_type_id"))
                    .orElseThrow(() -> new GeneralBadRequestException("Contract type missing during restore")));
            contract.setPaperType(PaperType.valueOf(requireCell(cells, 4, "paper_type")));
            contract.setCustomerId(cell(cells, 5));
            contract.setPartyName(cell(cells, 6));
            contract.setEffectiveDate(parseDate(cell(cells, 7), "effective_date"));
            contract.setExpiryDate(parseDate(cell(cells, 8), "expiry_date"));
            contract.setEvergreen(parseBoolean(cell(cells, 9)));
            contract.setStatus(ContractStatus.valueOf(requireCell(cells, 10, "status")));
            contract.setParentContractId(null);
            contract.setContractValue(parseDecimal(cell(cells, 12), "contract_value"));
            contract.setBillingCurrency(cell(cells, 13));
            contract.setPaymentTerms(cell(cells, 14));
            contract.setReminderDaysOverride(parseReminder(cell(cells, 15)));
            contract.setDescription(cell(cells, 16));
            contract.setOwnerUserId(resolveOwner(cells));
            var created = parseInstant(cell(cells, 18), "created_at");
            contract.setCreatedAt(created == null ? java.time.Instant.now() : created);
            contract.setCreatedBy(requireCell(cells, 19, "created_by"));
            contract.setUpdatedAt(parseInstant(cell(cells, 20), "updated_at"));
            contract.setUpdatedBy(cell(cells, 21));
            contractRepository.save(contract);
            ids.add(id);
            String parent = cell(cells, 11);
            parents.add(parent == null ? null : UUID.fromString(parent));
        }
        contractRepository.flush();
        for (int i = 0; i < ids.size(); i++) {
            UUID parentId = parents.get(i);
            if (parentId == null) {
                continue;
            }
            Contract contract = contractRepository.findById(ids.get(i)).orElseThrow();
            contract.setParentContractId(parentId);
            contractRepository.save(contract);
        }
        return ids.size();
    }

    private int restoreVersions(List<String[]> rows) {
        int count = 0;
        for (String[] cells : rows) {
            var version = new ContractVersion();
            version.setId(uuid(cells, 0, "id"));
            version.setContractId(uuid(cells, 1, "contract_id"));
            version.setVersionNumber(parseIntRequired(cell(cells, 2), "version_number"));
            version.setVersionLabel(requireCell(cells, 3, "version_label"));
            version.setStatus(VersionStatus.valueOf(requireCell(cells, 4, "status")));
            version.setNotes(cell(cells, 5));
            var uploaded = parseInstant(cell(cells, 6), "uploaded_at");
            version.setUploadedAt(uploaded == null ? java.time.Instant.now() : uploaded);
            version.setUploadedBy(requireCell(cells, 7, "uploaded_by"));
            contractVersionRepository.save(version);
            count++;
        }
        return count;
    }

    private int restoreDocuments(List<String[]> rows, Map<String, byte[]> blobs) {
        int count = 0;
        for (String[] cells : rows) {
            var document = new ContractDocument();
            document.setId(uuid(cells, 0, "id"));
            document.setContractVersionId(uuid(cells, 1, "contract_version_id"));
            document.setDocumentType(DocumentType.valueOf(requireCell(cells, 2, "document_type")));
            document.setFilename(requireCell(cells, 3, "filename"));
            document.setContentType(requireCell(cells, 4, "content_type"));
            String blobEntry = requireCell(cells, 6, "blob_entry");
            byte[] data = blobs.get(blobEntry);
            if (data == null) {
                throw new GeneralBadRequestException("Missing contract document blob " + blobEntry);
            }
            document.setFileData(data);
            document.setFileSizeBytes(data.length);
            var uploaded = parseInstant(cell(cells, 7), "uploaded_at");
            document.setUploadedAt(uploaded == null ? java.time.Instant.now() : uploaded);
            document.setUploadedBy(requireCell(cells, 8, "uploaded_by"));
            contractDocumentRepository.save(document);
            count++;
        }
        return count;
    }

    private int restoreTemplates(List<String[]> rows) {
        int count = 0;
        for (String[] cells : rows) {
            var template = new ContractTemplate();
            template.setId(uuid(cells, 0, "id"));
            template.setContractType(contractTypeRepository.findById(uuid(cells, 1, "contract_type_id"))
                    .orElseThrow(() -> new GeneralBadRequestException("Contract type missing during template restore")));
            template.setTemplateName(requireCell(cells, 2, "template_name"));
            template.setDescription(cell(cells, 3));
            template.setActive(parseBoolean(cell(cells, 4)));
            var created = parseInstant(cell(cells, 5), "created_at");
            template.setCreatedAt(created == null ? java.time.Instant.now() : created);
            template.setCreatedBy(requireCell(cells, 6, "created_by"));
            contractTemplateRepository.save(template);
            count++;
        }
        contractTemplateRepository.flush();
        return count;
    }

    private int restoreTemplateDocuments(List<String[]> rows, Map<String, byte[]> blobs) {
        int count = 0;
        for (String[] cells : rows) {
            var document = new ContractTemplateDocument();
            document.setId(uuid(cells, 0, "id"));
            document.setTemplateId(uuid(cells, 1, "template_id"));
            document.setVersionNumber(parseIntRequired(cell(cells, 2), "version_number"));
            document.setFilename(requireCell(cells, 3, "filename"));
            document.setContentType(requireCell(cells, 4, "content_type"));
            String blobEntry = requireCell(cells, 6, "blob_entry");
            byte[] data = blobs.get(blobEntry);
            if (data == null) {
                throw new GeneralBadRequestException("Missing template document blob " + blobEntry);
            }
            document.setFileData(data);
            document.setFileSizeBytes(data.length);
            var uploaded = parseInstant(cell(cells, 7), "uploaded_at");
            document.setUploadedAt(uploaded == null ? java.time.Instant.now() : uploaded);
            document.setUploadedBy(requireCell(cells, 8, "uploaded_by"));
            contractTemplateDocumentRepository.save(document);
            count++;
        }
        return count;
    }

    private int restoreLogs(List<String[]> rows) {
        int count = 0;
        for (String[] cells : rows) {
            var entry = new ContractNotificationLog();
            entry.setId(uuid(cells, 0, "id"));
            entry.setContractId(uuid(cells, 1, "contract_id"));
            entry.setNotificationType(ContractNotificationType.valueOf(requireCell(cells, 2, "notification_type")));
            entry.setDaysBeforeExpiry(parseIntRequired(cell(cells, 3), "days_before_expiry"));
            var sent = parseInstant(cell(cells, 4), "sent_at");
            entry.setSentAt(sent == null ? java.time.Instant.now() : sent);
            entry.setRecipients(cell(cells, 5));
            contractNotificationLogRepository.save(entry);
            count++;
        }
        return count;
    }

    private void alignSequence() {
        Number max = (Number) entityManager.createNativeQuery("""
                SELECT COALESCE(MAX(CAST(substring(contract_number from '[0-9]+$') AS INTEGER)), 0)
                FROM contract
                """).getSingleResult();
        boolean called = max != null && max.longValue() > 0;
        long value = called ? max.longValue() : 1;
        entityManager.createNativeQuery(
                        "SELECT setval('contract_number_seq', " + value + ", " + called + ")")
                .getSingleResult();
    }

    private UUID resolveOwner(String[] cells) {
        UUID ownerId = uuid(cells, 17, "owner_user_id");
        try {
            userService.requireById(ownerId);
            return ownerId;
        } catch (GeneralBadRequestException ex) {
            String email = cell(cells, 22);
            if (email == null) {
                throw new GeneralBadRequestException("Contract owner not found during restore");
            }
            return userService.findByEmail(email)
                    .map(AppUser::getId)
                    .orElseThrow(() -> new GeneralBadRequestException("Contract owner not found during restore"));
        }
    }

    private String ownerEmail(UUID ownerUserId) {
        try {
            return userService.requireById(ownerUserId).getEmail();
        } catch (GeneralBadRequestException ex) {
            return "";
        }
    }

    private static UUID uuid(String[] cells, int index, String field) {
        return UUID.fromString(requireCell(cells, index, field));
    }

    private static String reminderCsv(Integer[] days) {
        if (days == null || days.length == 0) {
            return "";
        }
        return Arrays.stream(days).map(String::valueOf).collect(Collectors.joining(","));
    }

    private static Integer[] parseReminder(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        Integer[] days = new Integer[parts.length];
        for (int i = 0; i < parts.length; i++) {
            days[i] = Integer.valueOf(parts[i].trim());
        }
        return days;
    }
}

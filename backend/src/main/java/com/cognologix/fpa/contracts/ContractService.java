package com.cognologix.fpa.contracts;

import com.cognologix.fpa.contracts.ContractDtos.AddVersionRequest;
import com.cognologix.fpa.contracts.ContractDtos.ContractDashboard;
import com.cognologix.fpa.contracts.ContractDtos.ContractDetail;
import com.cognologix.fpa.contracts.ContractDtos.ContractDocumentDownload;
import com.cognologix.fpa.contracts.ContractDtos.ContractNotificationConfig;
import com.cognologix.fpa.contracts.ContractDtos.ContractSummary;
import com.cognologix.fpa.contracts.ContractDtos.ContractTypeResponse;
import com.cognologix.fpa.contracts.ContractDtos.CountByLabel;
import com.cognologix.fpa.contracts.ContractDtos.CreateContractRequest;
import com.cognologix.fpa.contracts.ContractDtos.CreateContractTypeRequest;
import com.cognologix.fpa.contracts.ContractDtos.CreateTemplateRequest;
import com.cognologix.fpa.contracts.ContractDtos.DocumentMeta;
import com.cognologix.fpa.contracts.ContractDtos.NotificationHistoryEntry;
import com.cognologix.fpa.contracts.ContractDtos.TemplateGroup;
import com.cognologix.fpa.contracts.ContractDtos.TemplateSummary;
import com.cognologix.fpa.contracts.ContractDtos.UpcomingNotification;
import com.cognologix.fpa.contracts.ContractDtos.UpdateContractRequest;
import com.cognologix.fpa.contracts.ContractDtos.UpdateContractTypeRequest;
import com.cognologix.fpa.contracts.ContractDtos.VersionResponse;
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
import com.cognologix.fpa.contracts.backup.ContractModuleBackup;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.general.BackupSheet;
import com.cognologix.fpa.general.AppUser;
import com.cognologix.fpa.general.GeneralConfigService;
import com.cognologix.fpa.general.NotificationService;
import com.cognologix.fpa.general.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractService {

    public static final String REMINDER_DAYS_KEY = "contract_reminder_days";
    public static final String RECIPIENTS_KEY = "contract_notification_recipients";
    public static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final Set<String> OFFICE_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Set<String> OFFICE_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ContractRepository contractRepository;
    private final ContractTypeRepository contractTypeRepository;
    private final ContractVersionRepository contractVersionRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final ContractTemplateRepository contractTemplateRepository;
    private final ContractTemplateDocumentRepository contractTemplateDocumentRepository;
    private final ContractNotificationLogRepository contractNotificationLogRepository;
    private final CustomerService customerService;
    private final UserService userService;
    private final GeneralConfigService generalConfigService;
    private final NotificationService notificationService;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final TransactionTemplate transactionTemplate;
    private final ContractModuleBackup contractModuleBackup;

    @Value("${app.public-base-url:http://localhost:3000}")
    private String publicBaseUrl;

    @Value("${app.mail.from:noreply@cognologix.com}")
    private String mailFrom;

    @Transactional
    public ContractDetail createContract(CreateContractRequest request) {
        AppUser actor = currentUser();
        Contract contract = new Contract();
        contract.setContractNumber(nextContractNumber());
        contract.setCreatedAt(Instant.now());
        contract.setCreatedBy(actor.getEmail());
        applyMetadata(contract, request.title(), request.contractTypeId(), request.paperType(),
                request.customerId(), request.partyName(), request.effectiveDate(), request.expiryDate(),
                request.evergreen(), request.status() == null ? ContractStatus.ACTIVE : request.status(),
                request.parentContractId(), request.contractValue(), request.billingCurrency(),
                request.paymentTerms(), request.reminderDaysOverride(), request.description(),
                request.ownerUserId() == null ? actor.getId() : request.ownerUserId(),
                null);
        return toDetail(contractRepository.save(contract));
    }

    @Transactional
    public ContractDetail updateContract(UUID id, UpdateContractRequest request) {
        Contract contract = requireContract(id);
        AppUser actor = currentUser();
        applyMetadata(contract, request.title(), request.contractTypeId(), request.paperType(),
                request.customerId(), request.partyName(), request.effectiveDate(), request.expiryDate(),
                request.evergreen(), request.status(), request.parentContractId(), request.contractValue(),
                request.billingCurrency(), request.paymentTerms(), request.reminderDaysOverride(),
                request.description(), request.ownerUserId(), contract.getOwnerUserId());
        contract.setUpdatedAt(Instant.now());
        contract.setUpdatedBy(actor.getEmail());
        return toDetail(contractRepository.save(contract));
    }

    @Transactional(readOnly = true)
    public ContractDetail getContract(UUID id) {
        return toDetail(requireContract(id));
    }

    @Transactional(readOnly = true)
    public Page<ContractSummary> listContracts(
            UUID typeId,
            PaperType paperType,
            ContractStatus status,
            String customerId,
            LocalDate expiryFrom,
            LocalDate expiryTo,
            String search,
            Pageable pageable) {
        Map<String, String> customerNames = customerNames();
        Set<String> matchingCustomerIds = matchingCustomerIds(search, customerNames);
        Specification<Contract> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (typeId != null) {
                predicates.add(cb.equal(root.get("contractType").get("id"), typeId));
            }
            if (paperType != null) {
                predicates.add(cb.equal(root.get("paperType"), paperType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (customerId != null && !customerId.isBlank()) {
                predicates.add(cb.equal(root.get("customerId"), customerId.trim()));
            }
            if (expiryFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expiryDate"), expiryFrom));
            }
            if (expiryTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expiryDate"), expiryTo));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                List<Predicate> ors = new ArrayList<>();
                ors.add(cb.like(cb.lower(root.get("title")), like));
                ors.add(cb.like(cb.lower(cb.coalesce(root.get("partyName"), "")), like));
                if (!matchingCustomerIds.isEmpty()) {
                    ors.add(root.get("customerId").in(matchingCustomerIds));
                }
                predicates.add(cb.or(ors.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Map<UUID, AppUser> users = usersById();
        return contractRepository.findAll(spec, pageable)
                .map(contract -> toSummary(contract, customerNames, users));
    }

    @Transactional
    public VersionResponse addVersion(
            UUID contractId,
            AddVersionRequest request,
            MultipartFile primaryFile,
            List<MultipartFile> supportingFiles) {
        requireContract(contractId);
        VersionStatus requested = request.status() == null ? VersionStatus.DRAFT : request.status();
        if (requested == VersionStatus.SUPERSEDED) {
            throw new ContractBadRequestException(
                    "SUPERSEDED is set automatically when a newer version is uploaded");
        }
        StoredFile primary = readOfficeFile(primaryFile, "Primary document");
        List<StoredFile> supporting = new ArrayList<>();
        if (supportingFiles != null) {
            for (MultipartFile file : supportingFiles) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                supporting.add(readAnyFile(file));
            }
        }

        contractVersionRepository
                .findFirstByContractIdAndStatusNotOrderByVersionNumberDesc(contractId, VersionStatus.SUPERSEDED)
                .ifPresent(current -> {
                    current.setStatus(VersionStatus.SUPERSEDED);
                    contractVersionRepository.saveAndFlush(current);
                });
        if (requested == VersionStatus.SIGNED
                && contractVersionRepository.existsByContractIdAndStatus(contractId, VersionStatus.SIGNED)) {
            throw new ContractBadRequestException("Another version of this contract is already SIGNED");
        }

        String actor = currentUser().getEmail();
        var version = new ContractVersion();
        version.setContractId(contractId);
        version.setVersionNumber(contractVersionRepository.maxVersionNumber(contractId) + 1);
        version.setVersionLabel(request.versionLabel().trim());
        version.setStatus(requested);
        version.setNotes(blankToNull(request.notes()));
        version.setUploadedAt(Instant.now());
        version.setUploadedBy(actor);
        version = contractVersionRepository.save(version);

        saveDocument(version.getId(), DocumentType.PRIMARY, primary, actor);
        for (StoredFile file : supporting) {
            saveDocument(version.getId(), DocumentType.SUPPORTING, file, actor);
        }
        return toVersion(version, documentsFor(List.of(version.getId())).getOrDefault(version.getId(), List.of()));
    }

    @Transactional(readOnly = true)
    public ContractDocumentDownload downloadDocument(UUID documentId) {
        ContractDocument document = contractDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ContractNotFoundException("Document not found"));
        return new ContractDocumentDownload(document.getFilename(), document.getContentType(), document.getFileData());
    }

    @Transactional(readOnly = true)
    public void assertVersionOnContract(UUID contractId, UUID versionId) {
        ContractVersion version = contractVersionRepository.findById(versionId)
                .orElseThrow(() -> new ContractNotFoundException("Version not found"));
        if (!version.getContractId().equals(contractId)) {
            throw new ContractNotFoundException("Version not found");
        }
    }

    @Transactional(readOnly = true)
    public void assertDocumentOnContract(UUID contractId, UUID versionId, UUID documentId) {
        ContractDocument document = contractDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ContractNotFoundException("Document not found"));
        if (!document.getContractVersionId().equals(versionId)) {
            throw new ContractNotFoundException("Document not found");
        }
        ContractVersion version = contractVersionRepository.findById(versionId)
                .orElseThrow(() -> new ContractNotFoundException("Version not found"));
        if (!version.getContractId().equals(contractId)) {
            throw new ContractNotFoundException("Version not found");
        }
    }

    @Transactional
    public VersionResponse updateVersionStatus(UUID versionId, VersionStatus status) {
        if (status == null) {
            throw new ContractBadRequestException("Status is required");
        }
        if (status == VersionStatus.SUPERSEDED) {
            throw new ContractBadRequestException(
                    "SUPERSEDED is set automatically when a newer version is uploaded");
        }
        ContractVersion version = contractVersionRepository.findById(versionId)
                .orElseThrow(() -> new ContractNotFoundException("Version not found"));
        if (status == VersionStatus.SIGNED
                && contractVersionRepository.existsByContractIdAndStatusAndIdNot(
                        version.getContractId(), VersionStatus.SIGNED, version.getId())) {
            throw new ContractBadRequestException("Another version of this contract is already SIGNED");
        }
        version.setStatus(status);
        List<DocumentMeta> documents = documentsFor(List.of(version.getId()))
                .getOrDefault(version.getId(), List.of());
        return toVersion(contractVersionRepository.save(version), documents);
    }

    @Transactional(readOnly = true)
    public ContractDashboard getDashboardData() {
        LocalDate today = LocalDate.now(IST);
        Map<String, String> names = customerNames();
        Map<UUID, AppUser> users = usersById();
        List<ContractSummary> in30 = summariesBetween(today, today.plusDays(30), names, users);
        List<ContractSummary> in60 = summariesBetween(today.plusDays(31), today.plusDays(60), names, users);
        List<ContractSummary> in90 = summariesBetween(today.plusDays(61), today.plusDays(90), names, users);
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<ContractSummary> recent = contractRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(since)
                .stream()
                .map(contract -> toSummary(contract, names, users))
                .toList();

        List<CountByLabel> byType = contractRepository.countByTypeName().stream()
                .map(row -> new CountByLabel((String) row[0], (Long) row[1]))
                .toList();
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (ContractStatus value : ContractStatus.values()) {
            statusCounts.put(value.name(), 0L);
        }
        for (Object[] row : contractRepository.countByStatus()) {
            statusCounts.put(((ContractStatus) row[0]).name(), (Long) row[1]);
        }
        List<CountByLabel> byStatus = statusCounts.entrySet().stream()
                .map(entry -> new CountByLabel(entry.getKey(), entry.getValue()))
                .toList();
        return new ContractDashboard(in30, in60, in90, recent, byType, byStatus);
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    public void runExpiryNotifications() {
        List<Integer> defaults = reminderDaysFromConfig();
        List<String> configuredRecipients = recipientEmailsFromConfig();
        LocalDate today = LocalDate.now(IST);
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Spring Mail is not configured (spring.mail.host). Contract expiry emails will be skipped.");
        }
        for (Contract candidate : contractRepository.findExpiryCandidates()) {
            int days = (int) ChronoUnit.DAYS.between(today, candidate.getExpiryDate());
            List<Integer> reminders = reminderDays(candidate, defaults);
            if (!reminders.contains(days)) {
                continue;
            }
            UUID contractId = candidate.getId();
            try {
                transactionTemplate.executeWithoutResult(status ->
                        deliverExpiryNotification(contractId, days, configuredRecipients, sender));
            } catch (RuntimeException ex) {
                log.error("Contract expiry notification failed for {}: {}", contractId, ex.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ContractTypeResponse> listTypes(boolean includeInactive) {
        List<ContractType> types = includeInactive
                ? contractTypeRepository.findAllByOrderByDisplayNameAsc()
                : contractTypeRepository.findByActiveTrueOrderByDisplayNameAsc();
        return types.stream().map(ContractService::toType).toList();
    }

    @Transactional
    public ContractTypeResponse addType(CreateContractTypeRequest request) {
        String code = request.typeCode().trim().toUpperCase(Locale.ROOT);
        if (contractTypeRepository.findByTypeCodeIgnoreCase(code).isPresent()) {
            throw new ContractBadRequestException("Contract type code already exists");
        }
        var type = new ContractType();
        type.setTypeCode(code);
        type.setDisplayName(request.displayName().trim());
        type.setDescription(blankToNull(request.description()));
        type.setActive(true);
        type.setCreatedAt(Instant.now());
        return toType(contractTypeRepository.save(type));
    }

    @Transactional
    public ContractTypeResponse updateType(UUID id, UpdateContractTypeRequest request) {
        ContractType type = contractTypeRepository.findById(id)
                .orElseThrow(() -> new ContractNotFoundException("Contract type not found"));
        type.setDisplayName(request.displayName().trim());
        type.setDescription(blankToNull(request.description()));
        type.setActive(request.active());
        return toType(contractTypeRepository.save(type));
    }

    @Transactional(readOnly = true)
    public List<TemplateGroup> listTemplates() {
        List<ContractTemplate> templates = contractTemplateRepository.findByActiveTrueOrderByTemplateNameAsc();
        Map<UUID, ContractDtos.TemplateDocumentMeta> latest = Map.of();
        if (!templates.isEmpty()) {
            latest = contractTemplateDocumentRepository
                    .findLatestMeta(templates.stream().map(ContractTemplate::getId).toList())
                    .stream()
                    .collect(Collectors.toMap(ContractDtos.TemplateDocumentMeta::templateId, meta -> meta));
        }
        Map<UUID, List<TemplateSummary>> byType = new LinkedHashMap<>();
        for (ContractTemplate template : templates) {
            ContractDtos.TemplateDocumentMeta meta = latest.get(template.getId());
            var summary = new TemplateSummary(
                    template.getId(),
                    template.getContractType().getId(),
                    template.getTemplateName(),
                    template.getDescription(),
                    meta == null ? 0 : meta.versionNumber(),
                    meta == null ? null : meta.filename(),
                    meta == null ? null : meta.contentType(),
                    meta == null ? 0 : meta.fileSizeBytes(),
                    meta == null ? template.getCreatedAt() : meta.uploadedAt(),
                    meta == null ? template.getCreatedBy() : meta.uploadedBy());
            byType.computeIfAbsent(template.getContractType().getId(), ignored -> new ArrayList<>()).add(summary);
        }
        List<TemplateGroup> groups = new ArrayList<>();
        for (ContractType type : contractTypeRepository.findByActiveTrueOrderByDisplayNameAsc()) {
            groups.add(new TemplateGroup(
                    type.getId(), type.getTypeCode(), type.getDisplayName(),
                    byType.getOrDefault(type.getId(), List.of())));
        }
        return groups;
    }

    @Transactional
    public TemplateSummary createTemplate(CreateTemplateRequest request, MultipartFile file) {
        ContractType type = requireActiveType(request.contractTypeId());
        StoredFile stored = readOfficeFile(file, "Template document");
        String actor = currentUser().getEmail();
        var template = new ContractTemplate();
        template.setContractType(type);
        template.setTemplateName(request.templateName().trim());
        template.setDescription(blankToNull(request.description()));
        template.setActive(true);
        template.setCreatedAt(Instant.now());
        template.setCreatedBy(actor);
        template = contractTemplateRepository.save(template);

        var document = new ContractTemplateDocument();
        document.setTemplateId(template.getId());
        document.setVersionNumber(1);
        document.setFilename(stored.filename());
        document.setContentType(stored.contentType());
        document.setFileSizeBytes(stored.data().length);
        document.setFileData(stored.data());
        document.setUploadedAt(Instant.now());
        document.setUploadedBy(actor);
        document = contractTemplateDocumentRepository.save(document);
        return new TemplateSummary(
                template.getId(),
                type.getId(),
                template.getTemplateName(),
                template.getDescription(),
                document.getVersionNumber(),
                document.getFilename(),
                document.getContentType(),
                document.getFileSizeBytes(),
                document.getUploadedAt(),
                document.getUploadedBy());
    }

    @Transactional(readOnly = true)
    public ContractDocumentDownload downloadLatestTemplate(UUID templateId) {
        if (!contractTemplateRepository.existsById(templateId)) {
            throw new ContractNotFoundException("Template not found");
        }
        ContractTemplateDocument document = contractTemplateDocumentRepository
                .findFirstByTemplateIdOrderByVersionNumberDesc(templateId)
                .orElseThrow(() -> new ContractNotFoundException("Template document not found"));
        return new ContractDocumentDownload(document.getFilename(), document.getContentType(), document.getFileData());
    }

    @Transactional(readOnly = true)
    public ContractNotificationConfig getNotificationConfig() {
        return new ContractNotificationConfig(
                generalConfigService.getConfigValue(REMINDER_DAYS_KEY).orElse("90,60,30,7"),
                recipientEmailsFromConfig());
    }

    @Transactional
    public ContractNotificationConfig updateNotificationConfig(String reminderDays, List<String> recipients) {
        List<Integer> days = parseReminderDays(reminderDays);
        if (days.isEmpty()) {
            throw new ContractBadRequestException("At least one reminder day is required");
        }
        String storedDays = days.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<String> emails = normalizeEmails(recipients);
        generalConfigService.setConfigValue(REMINDER_DAYS_KEY, storedDays);
        generalConfigService.setConfigValue(RECIPIENTS_KEY, String.join(",", emails));
        return new ContractNotificationConfig(storedDays, emails);
    }

    public List<BackupSheet> exportBackupSheets() {
        return contractModuleBackup.exportSheets();
    }

    public Map<String, byte[]> exportDocumentBlobs() {
        return contractModuleBackup.exportBlobs();
    }

    @Transactional
    public void wipeForRestore() {
        contractModuleBackup.wipe();
    }

    @Transactional
    public Map<String, Integer> restoreBackupSheets(
            Map<String, List<String[]>> rowsByFile, Map<String, byte[]> blobs) {
        return contractModuleBackup.restore(rowsByFile, blobs);
    }

    private void deliverExpiryNotification(
            UUID contractId, int days, List<String> configuredRecipients, JavaMailSender sender) {
        Contract contract = contractRepository.findById(contractId).orElse(null);
        if (contract == null || contract.isEvergreen() || contract.getExpiryDate() == null) {
            return;
        }
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            return;
        }
        AppUser owner = userService.requireById(contract.getOwnerUserId());
        String party = partyDisplay(contract, customerNames());
        String typeName = contract.getContractType().getDisplayName();
        String link = "/contracts/" + contract.getId();
        String title = "Contract expiring in " + days + " days";
        String message = contract.getTitle() + " (" + party + ") expires on " + contract.getExpiryDate()
                + ". " + days + " days remaining.";

        if (!contractNotificationLogRepository.existsByContractIdAndNotificationTypeAndDaysBeforeExpiry(
                contractId, ContractNotificationType.IN_APP, days)) {
            notificationService.notifyUser(owner.getId(), title, message, link);
            logNotification(contractId, ContractNotificationType.IN_APP, days, List.of(owner.getEmail()));
        }

        if (sender == null) {
            return;
        }
        if (contractNotificationLogRepository.existsByContractIdAndNotificationTypeAndDaysBeforeExpiry(
                contractId, ContractNotificationType.EMAIL, days)) {
            return;
        }
        LinkedHashSet<String> recipients = new LinkedHashSet<>();
        if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
            recipients.add(owner.getEmail().trim().toLowerCase(Locale.ROOT));
        }
        recipients.addAll(configuredRecipients);
        if (recipients.isEmpty()) {
            return;
        }
        String url = publicBaseUrl.replaceAll("/$", "") + link;
        var mail = new SimpleMailMessage();
        mail.setFrom(mailFrom);
        mail.setTo(recipients.toArray(String[]::new));
        mail.setSubject(title + ": " + contract.getTitle());
        mail.setText("""
                Contract: %s
                Number: %s
                Party: %s
                Type: %s
                Expiry date: %s
                Days remaining: %d

                Open in FPA: %s
                """.formatted(
                contract.getTitle(),
                contract.getContractNumber(),
                party,
                typeName,
                contract.getExpiryDate(),
                days,
                url));
        try {
            sender.send(mail);
            logNotification(contractId, ContractNotificationType.EMAIL, days, List.copyOf(recipients));
        } catch (MailException ex) {
            log.error("Failed to email contract expiry notice for {}: {}", contract.getContractNumber(), ex.getMessage());
        }
    }

    private void logNotification(
            UUID contractId, ContractNotificationType type, int days, List<String> recipients) {
        var logRow = new ContractNotificationLog();
        logRow.setContractId(contractId);
        logRow.setNotificationType(type);
        logRow.setDaysBeforeExpiry(days);
        logRow.setSentAt(Instant.now());
        logRow.setRecipients(toJsonArray(recipients));
        contractNotificationLogRepository.save(logRow);
    }

    private void applyMetadata(
            Contract contract,
            String title,
            UUID typeId,
            PaperType paperType,
            String customerId,
            String partyName,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            boolean evergreen,
            ContractStatus status,
            UUID parentContractId,
            BigDecimal contractValue,
            String billingCurrency,
            String paymentTerms,
            List<Integer> reminderDaysOverride,
            String description,
            UUID ownerUserId,
            UUID previousOwnerId) {
        if (title == null || title.isBlank()) {
            throw new ContractBadRequestException("Title is required");
        }
        if (paperType == null) {
            throw new ContractBadRequestException("Paper type is required");
        }
        if (status == null) {
            throw new ContractBadRequestException("Status is required");
        }
        ContractType type = contractTypeRepository.findById(typeId)
                .orElseThrow(() -> new ContractBadRequestException("Contract type not found"));
        boolean sameType = contract.getContractType() != null && contract.getContractType().getId().equals(typeId);
        if (!type.isActive() && !sameType) {
            throw new ContractBadRequestException("Contract type is not active");
        }
        String customer = blankToNull(customerId);
        String party = blankToNull(partyName);
        if (customer != null && party != null) {
            throw new ContractBadRequestException("Provide either an FPA customer or a party name, not both");
        }
        if (customer == null && party == null) {
            throw new ContractBadRequestException("Client is required");
        }
        if (customer != null) {
            UUID customerUuid;
            try {
                customerUuid = UUID.fromString(customer);
            } catch (IllegalArgumentException ex) {
                throw new ContractBadRequestException("Customer not found");
            }
            if (customerService.findCustomerRef(customerUuid).isEmpty()) {
                throw new ContractBadRequestException("Customer not found");
            }
            customer = customerUuid.toString();
        }
        if (parentContractId != null) {
            if (contract.getId() != null && parentContractId.equals(contract.getId())) {
                throw new ContractBadRequestException("A contract cannot be its own parent");
            }
            if (!contractRepository.existsById(parentContractId)) {
                throw new ContractBadRequestException("Parent contract not found");
            }
        }
        if (contractValue != null && contractValue.signum() < 0) {
            throw new ContractBadRequestException("Contract value cannot be negative");
        }
        String currency = blankToNull(billingCurrency);
        if (currency != null) {
            currency = currency.toUpperCase(Locale.ROOT);
            if (!currency.equals("USD") && !currency.equals("INR")) {
                throw new ContractBadRequestException("Billing currency must be USD or INR");
            }
        }
        String terms = blankToNull(paymentTerms);
        if (terms != null && terms.length() > 255) {
            throw new ContractBadRequestException("Payment terms must be 255 characters or fewer");
        }
        AppUser owner;
        try {
            owner = userService.requireById(ownerUserId);
        } catch (com.cognologix.fpa.general.GeneralBadRequestException ex) {
            throw new ContractBadRequestException("Contract owner not found");
        }
        if (!owner.isActive() && (previousOwnerId == null || !previousOwnerId.equals(ownerUserId))) {
            throw new ContractBadRequestException("Contract owner must be an active user");
        }
        if (evergreen) {
            expiryDate = null;
        }

        contract.setTitle(title.trim());
        contract.setContractType(type);
        contract.setPaperType(paperType);
        contract.setCustomerId(customer);
        contract.setPartyName(party);
        contract.setEffectiveDate(effectiveDate);
        contract.setExpiryDate(expiryDate);
        contract.setEvergreen(evergreen);
        contract.setStatus(status);
        contract.setParentContractId(parentContractId);
        contract.setContractValue(contractValue);
        contract.setBillingCurrency(currency);
        contract.setPaymentTerms(terms);
        contract.setReminderDaysOverride(toReminderArray(reminderDaysOverride));
        contract.setDescription(blankToNull(description));
        contract.setOwnerUserId(owner.getId());
    }

    private String nextContractNumber() {
        long seq = ((Number) contractRepository.nextContractNumber()).longValue();
        return "CON-" + LocalDate.now(IST).getYear() + "-" + String.format("%03d", seq);
    }

    private List<ContractSummary> summariesBetween(
            LocalDate from, LocalDate to, Map<String, String> names, Map<UUID, AppUser> users) {
        return contractRepository
                .findByStatusAndEvergreenFalseAndExpiryDateBetweenOrderByExpiryDateAsc(ContractStatus.ACTIVE, from, to)
                .stream()
                .map(contract -> toSummary(contract, names, users))
                .toList();
    }

    private ContractDetail toDetail(Contract contract) {
        Map<String, String> names = customerNames();
        Map<UUID, AppUser> users = usersById();
        AppUser owner = users.get(contract.getOwnerUserId());
        Contract parent = contract.getParentContractId() == null
                ? null
                : contractRepository.findById(contract.getParentContractId()).orElse(null);
        List<ContractVersion> versions = contractVersionRepository
                .findByContractIdOrderByVersionNumberDesc(contract.getId());
        Map<UUID, List<DocumentMeta>> documents = documentsFor(versions.stream().map(ContractVersion::getId).toList());
        List<Integer> reminderDays = reminderDays(contract, reminderDaysFromConfig());
        List<UpcomingNotification> upcoming = new ArrayList<>();
        if (!contract.isEvergreen() && contract.getExpiryDate() != null) {
            LocalDate today = LocalDate.now(IST);
            for (Integer day : reminderDays) {
                LocalDate when = contract.getExpiryDate().minusDays(day);
                if (!when.isBefore(today)) {
                    upcoming.add(new UpcomingNotification(when, day));
                }
            }
            upcoming.sort(Comparator.comparing(UpcomingNotification::notificationDate));
        }
        List<NotificationHistoryEntry> history = contractNotificationLogRepository
                .findByContractIdOrderBySentAtDesc(contract.getId())
                .stream()
                .map(row -> new NotificationHistoryEntry(
                        row.getId(),
                        row.getNotificationType(),
                        row.getDaysBeforeExpiry(),
                        row.getSentAt(),
                        row.getRecipients()))
                .toList();
        return new ContractDetail(
                contract.getId(),
                contract.getContractNumber(),
                contract.getTitle(),
                partyDisplay(contract, names),
                contract.getCustomerId(),
                contract.getPartyName(),
                contract.getContractType().getId(),
                contract.getContractType().getTypeCode(),
                contract.getContractType().getDisplayName(),
                contract.getPaperType(),
                contract.getStatus(),
                contract.getEffectiveDate(),
                contract.getExpiryDate(),
                contract.isEvergreen(),
                daysRemaining(contract),
                contract.getParentContractId(),
                parent == null ? null : parent.getContractNumber(),
                parent == null ? null : parent.getTitle(),
                contract.getContractValue(),
                contract.getBillingCurrency(),
                contract.getPaymentTerms(),
                toReminderList(contract.getReminderDaysOverride()),
                contract.getDescription(),
                contract.getOwnerUserId(),
                owner == null ? null : owner.getFullName(),
                owner == null ? null : owner.getEmail(),
                contract.getCreatedBy(),
                contract.getCreatedAt(),
                contract.getUpdatedAt(),
                contract.getUpdatedBy(),
                versions.stream()
                        .map(version -> toVersion(version, documents.getOrDefault(version.getId(), List.of())))
                        .toList(),
                upcoming,
                history);
    }

    private ContractSummary toSummary(Contract contract, Map<String, String> names, Map<UUID, AppUser> users) {
        AppUser owner = users.get(contract.getOwnerUserId());
        return new ContractSummary(
                contract.getId(),
                contract.getContractNumber(),
                contract.getTitle(),
                partyDisplay(contract, names),
                contract.getCustomerId(),
                contract.getPartyName(),
                contract.getContractType().getId(),
                contract.getContractType().getTypeCode(),
                contract.getContractType().getDisplayName(),
                contract.getPaperType(),
                contract.getStatus(),
                contract.getEffectiveDate(),
                contract.getExpiryDate(),
                contract.isEvergreen(),
                daysRemaining(contract),
                contract.getContractValue(),
                contract.getBillingCurrency(),
                contract.getOwnerUserId(),
                owner == null ? null : owner.getFullName(),
                contract.getCreatedAt());
    }

    private static VersionResponse toVersion(ContractVersion version, List<DocumentMeta> documents) {
        List<DocumentMeta> ordered = documents.stream()
                .sorted(Comparator.comparing((DocumentMeta doc) -> doc.documentType() == DocumentType.PRIMARY ? 0 : 1)
                        .thenComparing(DocumentMeta::filename, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        return new VersionResponse(
                version.getId(),
                version.getContractId(),
                version.getVersionNumber(),
                version.getVersionLabel(),
                version.getStatus(),
                version.getNotes(),
                version.getUploadedAt(),
                version.getUploadedBy(),
                ordered);
    }

    private Map<UUID, List<DocumentMeta>> documentsFor(List<UUID> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return contractDocumentRepository.findMetaByVersionIds(versionIds).stream()
                .collect(Collectors.groupingBy(DocumentMeta::contractVersionId));
    }

    private void saveDocument(UUID versionId, DocumentType type, StoredFile file, String actor) {
        var document = new ContractDocument();
        document.setContractVersionId(versionId);
        document.setDocumentType(type);
        document.setFilename(file.filename());
        document.setContentType(file.contentType());
        document.setFileSizeBytes(file.data().length);
        document.setFileData(file.data());
        document.setUploadedAt(Instant.now());
        document.setUploadedBy(actor);
        contractDocumentRepository.save(document);
    }

    private Contract requireContract(UUID id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ContractNotFoundException("Contract not found"));
    }

    private ContractType requireActiveType(UUID id) {
        ContractType type = contractTypeRepository.findById(id)
                .orElseThrow(() -> new ContractBadRequestException("Contract type not found"));
        if (!type.isActive()) {
            throw new ContractBadRequestException("Contract type is not active");
        }
        return type;
    }

    private AppUser currentUser() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ContractBadRequestException("Authenticated user is required");
        }
        return userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new ContractBadRequestException("User not found"));
    }

    private Map<String, String> customerNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (CustomerService.CustomerRef ref : customerService.listCustomerRefs(true)) {
            names.put(ref.id().toString(), ref.customerName());
        }
        return names;
    }

    private static Set<String> matchingCustomerIds(String search, Map<String, String> customerNames) {
        if (search == null || search.isBlank()) {
            return Set.of();
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        return customerNames.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().toLowerCase(Locale.ROOT).contains(needle))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Map<UUID, AppUser> usersById() {
        return userService.listAll().stream().collect(Collectors.toMap(AppUser::getId, user -> user));
    }

    private static String partyDisplay(Contract contract, Map<String, String> customerNames) {
        if (contract.getCustomerId() != null) {
            return customerNames.getOrDefault(contract.getCustomerId(), contract.getCustomerId());
        }
        return contract.getPartyName();
    }

    private static Integer daysRemaining(Contract contract) {
        if (contract.isEvergreen() || contract.getExpiryDate() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(IST), contract.getExpiryDate());
    }

    private List<Integer> reminderDaysFromConfig() {
        return parseReminderDays(generalConfigService.getConfigValue(REMINDER_DAYS_KEY).orElse("90,60,30,7"));
    }

    private List<String> recipientEmailsFromConfig() {
        String raw = generalConfigService.getConfigValue(RECIPIENTS_KEY).orElse("");
        if (raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1, trimmed.endsWith("]") ? trimmed.length() - 1 : trimmed.length());
            trimmed = trimmed.replace("\"", "");
        }
        return normalizeEmails(Arrays.asList(trimmed.split(",")));
    }

    private static List<Integer> reminderDays(Contract contract, List<Integer> defaults) {
        List<Integer> override = toReminderList(contract.getReminderDaysOverride());
        return override == null ? defaults : override;
    }

    private static List<Integer> parseReminderDays(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<Integer> days = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                int day = Integer.parseInt(part.trim());
                if (day <= 0) {
                    throw new ContractBadRequestException("Reminder days must be positive");
                }
                days.add(day);
            } catch (NumberFormatException ex) {
                throw new ContractBadRequestException("Reminder days must be comma-separated positive numbers");
            }
        }
        return days.stream().sorted(Comparator.reverseOrder()).toList();
    }

    private static Integer[] toReminderArray(List<Integer> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (Integer day : days) {
            if (day == null || day <= 0) {
                throw new ContractBadRequestException("Reminder days must be positive");
            }
            unique.add(day);
        }
        return unique.toArray(Integer[]::new);
    }

    private static List<Integer> toReminderList(Integer[] days) {
        if (days == null || days.length == 0) {
            return null;
        }
        return Arrays.stream(days).filter(day -> day != null && day > 0).toList();
    }

    private static List<String> normalizeEmails(List<String> recipients) {
        if (recipients == null) {
            return List.of();
        }
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (String recipient : recipients) {
            if (recipient == null || recipient.isBlank()) {
                continue;
            }
            String email = recipient.trim().toLowerCase(Locale.ROOT);
            if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new ContractBadRequestException("Invalid email address: " + recipient.trim());
            }
            emails.add(email);
        }
        return List.copyOf(emails);
    }

    private static String toJsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static StoredFile readOfficeFile(MultipartFile file, String label) {
        StoredFile stored = readAnyFile(file);
        String extension = extension(stored.filename());
        if (!OFFICE_EXTENSIONS.contains(extension)) {
            throw new ContractBadRequestException(label + " must be a PDF or Word file");
        }
        String contentType = stored.contentType();
        if (!OFFICE_CONTENT_TYPES.contains(contentType) && !"application/octet-stream".equals(contentType)) {
            throw new ContractBadRequestException(label + " must be a PDF or Word file");
        }
        return stored;
    }

    private static StoredFile readAnyFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ContractBadRequestException("A file is required");
        }
        if (file.getSize() > MAX_DOCUMENT_BYTES) {
            throw new ContractBadRequestException("File exceeds the 20MB limit");
        }
        String filename = filenameOf(file);
        if (filename.isBlank()) {
            throw new ContractBadRequestException("File name is required");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw new ContractBadRequestException("Failed to read uploaded file");
        }
        if (data.length > MAX_DOCUMENT_BYTES) {
            throw new ContractBadRequestException("File exceeds the 20MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        return new StoredFile(filename, contentType, data);
    }

    private static String filenameOf(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        int slash = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
        return slash >= 0 ? original.substring(slash + 1) : original;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ContractTypeResponse toType(ContractType type) {
        return new ContractTypeResponse(
                type.getId(), type.getTypeCode(), type.getDisplayName(), type.getDescription(), type.isActive());
    }

    private record StoredFile(String filename, String contentType, byte[] data) {}
}

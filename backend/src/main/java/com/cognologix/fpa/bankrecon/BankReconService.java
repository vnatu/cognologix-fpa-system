package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.*;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.*;
import com.cognologix.fpa.bankrecon.repository.*;
import com.cognologix.fpa.general.GeneralConfigService;
import com.cognologix.fpa.people.MappingTemplateApi;
import com.cognologix.fpa.people.PeoplePayrollService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankReconService {

    private static final Logger log = LoggerFactory.getLogger(BankReconService.class);
    static final String CFG_BATCH = "ollama_batch_size";
    static final String CFG_COMPANY = "ollama_company_name";
    static final String HDFC_IMPORT_TYPE = "HDFC_BANK_STATEMENT";
    private static final Set<String> HDFC_IMPORT_TYPES = Set.of(HDFC_IMPORT_TYPE);
    private static final double FUZZY_THRESHOLD = 0.6;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ReconRunRepository runRepository;
    private final ReconTransactionRepository transactionRepository;
    private final ReconExportRepository exportRepository;
    private final TallyLedgerRepository ledgerRepository;
    private final TallyLedgerGroupRepository ledgerGroupRepository;
    private final LearnedMappingRepository learnedMappingRepository;
    private final LlmHintRepository hintRepository;
    private final ContraRuleRepository contraRuleRepository;
    private final ReconAuditLogRepository auditLogRepository;
    private final ReconAccountMappingRepository accountMappingRepository;
    private final TallyLedgerHintRepository ledgerHintRepository;
    private final LearnedMappingVectorStore vectorStore;
    private final GeneralConfigService generalConfigService;
    private final PeoplePayrollService peoplePayrollService;
    private final StructuredLlmClient structuredLlmClient;
    private final NarrationEmbedder narrationEmbedder;
    private final HdfcStatementParser statementParser = new HdfcStatementParser();
    private final TallyPrimeExcelExporter excelExporter = new TallyPrimeExcelExporter();
    private final TallyLedgerXmlParser xmlParser = new TallyLedgerXmlParser();

    public ParseHeadersResponse parseStatementHeaders(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Statement file is required");
        }
        HdfcStatementParser.ParseHeadersResult result = statementParser.parseHeaders(file);
        return new ParseHeadersResponse(
                result.headers(),
                result.headerFields(),
                result.transactionColumns(),
                result.headerFieldValues(),
                result.rowCount());
    }

    public List<MappingTemplateApi> listActiveColumnMappings() {
        return peoplePayrollService.findActiveMappingApis(HDFC_IMPORT_TYPES);
    }

    public Optional<MappingTemplateApi> findActiveColumnMapping() {
        return peoplePayrollService.findActiveMappingApi(HDFC_IMPORT_TYPE);
    }

    @Transactional
    public MappingTemplateApi saveColumnMapping(
            String templateName, List<PeoplePayrollService.MappingLineInput> lines) {
        validateMappingLines(lines);
        return peoplePayrollService.saveMappingTemplateApi(HDFC_IMPORT_TYPE, templateName, lines);
    }

    public byte[] sampleMappingWorkbook() {
        Map<String, String> excelToAttr = findActiveColumnMapping()
                .map(this::toExcelColumnMap)
                .orElse(Map.of());
        return statementParser.sampleWorkbook(excelToAttr);
    }

    @Transactional
    public RunResponse uploadStatement(MultipartFile file, String uploadedBy) {
        return uploadStatement(file, null, uploadedBy);
    }

    @Transactional
    public RunResponse uploadStatement(MultipartFile file, UUID mappingId, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Statement file is required");
        }
        MappingTemplateApi mapping = resolveColumnMapping(mappingId);
        HdfcStatementParser.ParsedStatement parsed =
                statementParser.parse(file, toExcelColumnMap(mapping));
        String accountNumber = parsed.accountNumber() == null ? "" : parsed.accountNumber().trim();
        String bankLedger = requireBankLedger(accountNumber);
        ReconRun run = ReconRun.builder()
                .runNumber(nextRunNumber())
                .statementNumber(parsed.statementNumber())
                .accountNumber(parsed.accountNumber())
                .bankLedgerName(bankLedger)
                .customerName(parsed.customerName())
                .statementPeriodStart(parsed.periodStart())
                .statementPeriodEnd(parsed.periodEnd())
                .openingBalance(parsed.openingBalance())
                .closingBalance(parsed.closingBalance())
                .originalFilename(file.getOriginalFilename())
                .status(ReconRunStatus.OPEN)
                .createdBy(uploadedBy)
                .createdAt(Instant.now())
                .build();
        run = runRepository.save(run);

        List<ContraRule> contraRules = contraRuleRepository.findByActiveTrue();
        List<ReconTransaction> transactions = new ArrayList<>();
        int order = 0;
        for (HdfcStatementParser.ParsedRow row : parsed.rows()) {
            ReconTransaction tx = ReconTransaction.builder()
                    .runId(run.getId())
                    .transactionDate(row.transactionDate())
                    .description(row.description())
                    .normalisedDescription(NarrationNormalizer.normalise(row.description()))
                    .amount(row.amount())
                    .debitCredit(row.debitCredit())
                    .referenceNo(row.referenceNo())
                    .valueDate(row.valueDate())
                    .transactionBranch(row.branch())
                    .runningBalance(row.runningBalance())
                    .sortOrder(order++)
                    .build();
            tx.setVoucherType(classifyTransaction(tx, contraRules));
            transactions.add(tx);
        }
        transactionRepository.saveAll(transactions);
        runMappingPipeline(transactions);
        transactionRepository.saveAll(transactions);
        refreshRunCounts(run, transactions);
        runRepository.save(run);
        return toRunResponse(run, transactions, List.of());
    }

    public VoucherType classifyTransaction(ReconTransaction tx, List<ContraRule> contraRules) {
        String raw = tx.getDescription() == null ? "" : tx.getDescription();
        String normalised = tx.getNormalisedDescription() != null
                ? tx.getNormalisedDescription()
                : NarrationNormalizer.normalise(raw);
        String rawFolded = fold(raw);
        String normalisedFolded = fold(normalised);
        String company = fold(generalConfigService.getConfigValue(CFG_COMPANY).orElse("COGNOLOGIX"));
        boolean companyInRaw = !company.isEmpty() && rawFolded.contains(company);
        boolean companyInNormalised = !company.isEmpty() && normalisedFolded.contains(company);
        boolean companyMatch = companyInRaw || companyInNormalised;
        VoucherType fallback = tx.getDebitCredit() == DebitCredit.D ? VoucherType.PAYMENT : VoucherType.RECEIPT;
        List<ContraRule> rules = contraRules == null ? List.of() : contraRules;
        if (!companyMatch) {
            String reason = company.isEmpty()
                    ? "company name is empty"
                    : "company name '" + company + "' not in the narration";
            log.debug("Contra decision={} reason={} companyInRaw={} companyInNormalised={} rules={} narration={}",
                    fallback, reason, companyInRaw, companyInNormalised, ruleLabels(rules), raw);
            return fallback;
        }
        for (ContraRule rule : rules) {
            if (rule == null || !rule.isActive()) {
                continue;
            }
            String pattern = fold(rule.getPatternValue());
            if (pattern.isEmpty()) {
                continue;
            }
            boolean prefix = rule.getPatternType() == PatternType.PREFIX;
            boolean matches = prefix
                    ? rawFolded.startsWith(pattern) || normalisedFolded.startsWith(pattern)
                    : rawFolded.contains(pattern) || normalisedFolded.contains(pattern);
            if (matches) {
                log.debug("Contra decision=CONTRA matched={} {} company='{}' companyInRaw={} companyInNormalised={} narration={}",
                        rule.getPatternType(), pattern, company, companyInRaw, companyInNormalised, raw);
                return VoucherType.CONTRA;
            }
        }
        log.debug("Contra decision={} reason=company name '{}' matched but no rule matched companyInRaw={} companyInNormalised={} rules={} narration={}",
                fallback, company, companyInRaw, companyInNormalised, ruleLabels(rules), raw);
        return fallback;
    }

    private static String fold(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String ruleLabels(List<ContraRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return "(none)";
        }
        StringBuilder labels = new StringBuilder();
        for (ContraRule rule : rules) {
            if (rule == null || !rule.isActive()) {
                continue;
            }
            if (!labels.isEmpty()) {
                labels.append(", ");
            }
            labels.append(rule.getPatternType()).append(' ').append(fold(rule.getPatternValue()));
        }
        return labels.isEmpty() ? "(none)" : labels.toString();
    }

    public void runMappingPipeline(List<ReconTransaction> transactions) {
        boolean opened = LlmCallContext.openIfAbsent(runNumberOf(transactions));
        try {
            List<ReconTransaction> unmapped = new ArrayList<>();
            for (ReconTransaction tx : transactions) {
                if (tx.isExcluded() || tx.getVoucherType() == VoucherType.CONTRA) {
                    continue;
                }
                Optional<LearnedMapping> learned = learnedMappingRepository
                        .findByNormalisedNarrationAndVoucherType(tx.getNormalisedDescription(), tx.getVoucherType());
                if (learned.isPresent()) {
                    LearnedMapping mapping = learned.get();
                    tx.setMappedLedger(mapping.getLedgerName());
                    tx.setMappingSource(MappingSource.LEARNED);
                    mapping.setUseCount(mapping.getUseCount() + 1);
                    mapping.setLastUsedAt(Instant.now());
                    learnedMappingRepository.save(mapping);
                } else {
                    unmapped.add(tx);
                }
            }
            int batchSize = BankReconAiConfig.parseInt(
                    generalConfigService.getConfigValue(CFG_BATCH).orElse("10"), 10);
            if (batchSize < 1) {
                batchSize = 10;
            }
            for (int i = 0; i < unmapped.size(); i += batchSize) {
                List<ReconTransaction> batch = unmapped.subList(i, Math.min(i + batchSize, unmapped.size()));
                mapBatchWithLlm(batch);
            }
        } finally {
            if (opened) {
                LlmCallContext.close();
            }
        }
    }

    public void mapBatchWithLlm(List<ReconTransaction> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        List<ReconTransaction> mappable = batch.stream()
                .filter(tx -> tx.getVoucherType() != VoucherType.CONTRA)
                .toList();
        if (mappable.isEmpty()) {
            return;
        }
        boolean opened = LlmCallContext.openIfAbsent(runNumberOf(mappable));
        try {
            mapWithinTokenBudget(mappable);
        } finally {
            if (opened) {
                LlmCallContext.close();
            }
        }
    }

    private void mapWithinTokenBudget(List<ReconTransaction> batch) {
        String prompt = buildPrompt(batch);
        int tokens = MappingPrompt.estimateTokens(prompt);
        if (tokens > MappingPrompt.SAFE_PROMPT_TOKENS && batch.size() > 1) {
            int mid = batch.size() / 2;
            mapWithinTokenBudget(batch.subList(0, mid));
            mapWithinTokenBudget(batch.subList(mid, batch.size()));
            return;
        }
        mapCall(batch, prompt);
    }

    private void mapCall(List<ReconTransaction> batch, String prompt) {
        int rows = promptRowCount(batch);
        int maxTokens = MappingPrompt.completionTokenBudget(rows);
        LlmBatchResult result = structuredLlmClient.mapBatch(prompt, maxTokens);
        applyLlmMappings(batch, result);
        if (!result.truncated()) {
            return;
        }
        List<ReconTransaction> leftover = unparsedTransactions(batch, parsedPromptIds(result.mappings()));
        if (leftover.isEmpty() || leftover.size() >= rows) {
            if (!leftover.isEmpty()) {
                log.warn("callId={} LLM mapping truncated with no complete rows; leaving {} transactions unmapped",
                        LlmCallContext.activeCallId(), leftover.size());
            }
            return;
        }
        log.info("callId={} LLM mapping truncated; re-sending {} remaining transactions",
                LlmCallContext.activeCallId(), leftover.size());
        mapWithinTokenBudget(leftover);
    }

    private void applyLlmMappings(List<ReconTransaction> batch, LlmBatchResult result) {
        List<LedgerMappingResult> mappings = result.mappings();
        Map<String, String> byId = new LinkedHashMap<>();
        int duplicates = 0;
        List<String> rejections = new ArrayList<>();
        for (LedgerMappingResult mapping : mappings) {
            String id = mapping.transactionId();
            if (id == null || id.isBlank()) {
                continue;
            }
            if (byId.containsKey(id)) {
                duplicates++;
                addRejection(rejections, id, id, mapping.ledgerName(), "duplicate transaction id");
            } else {
                byId.put(id, mapping.ledgerName() == null ? "" : mapping.ledgerName());
            }
        }
        Map<VoucherType, Set<String>> allowedByType = new EnumMap<>(VoucherType.class);
        Set<String> promptIds = new HashSet<>();
        int accepted = 0;
        int rejected = duplicates;
        int unmapped = 0;
        int promptId = 1;
        for (ReconTransaction tx : batch) {
            if (tx.getVoucherType() == VoucherType.CONTRA) {
                continue;
            }
            String id = Integer.toString(promptId++);
            promptIds.add(id);
            Set<String> allowed = allowedByType.computeIfAbsent(tx.getVoucherType(), this::allowedLedgers);
            String ledger = byId.get(id);
            if (ledger == null) {
                tx.setMappedLedger(null);
                tx.setMappingSource(null);
                continue;
            }
            if (isUnmappedLedger(ledger)) {
                unmapped++;
                tx.setMappedLedger(null);
                tx.setMappingSource(null);
                continue;
            }
            if (ledger.isBlank()) {
                rejected++;
                addRejection(rejections, tx.getId().toString(), id, ledger, "blank ledger");
                tx.setMappedLedger(null);
                tx.setMappingSource(null);
                continue;
            }
            if (allowed.contains(ledger)) {
                accepted++;
                tx.setMappedLedger(ledger);
                tx.setMappingSource(MappingSource.LLM);
            } else {
                rejected++;
                addRejection(rejections, tx.getId().toString(), id, ledger, "not in catalog");
                tx.setMappedLedger(null);
                tx.setMappingSource(null);
            }
        }
        for (Map.Entry<String, String> entry : byId.entrySet()) {
            if (promptIds.contains(entry.getKey())) {
                continue;
            }
            if (isUnmappedLedger(entry.getValue())) {
                unmapped++;
            } else {
                rejected++;
                addRejection(rejections, entry.getKey(), entry.getKey(), entry.getValue(), "unknown transaction id");
            }
        }
        String callId = LlmCallContext.activeCallId();
        String apiKey = generalConfigService.getConfigValue(BankReconAiConfig.CFG_API_KEY).orElse("");
        LlmTrace.info(log, apiKey, "callId=" + callId
                + " rowsReturned=" + result.returned()
                + " parsed=" + result.parsed()
                + " accepted=" + accepted
                + " rejected=" + rejected
                + " unmapped=" + unmapped);
        for (String rejection : rejections) {
            LlmTrace.warn(log, apiKey, "callId=" + callId + " " + rejection);
        }
        if (result.replied() && result.parsed() == 0) {
            LlmTrace.warn(log, apiKey, "callId=" + callId
                    + " nothing parsed rawReply=" + LlmTrace.rawReplySample(result.rawText()));
        }
    }

    private static boolean isUnmappedLedger(String ledger) {
        return ledger != null && ledger.trim().equalsIgnoreCase("UNMAPPED");
    }

    private static void addRejection(
            List<String> rejections, String transactionId, String promptId, String ledger, String reason) {
        if (rejections.size() >= 5) {
            return;
        }
        String shown = ledger == null ? "" : ledger.replace('\r', ' ').replace('\n', ' ');
        rejections.add("rejected transactionId=" + transactionId
                + " promptId=" + promptId
                + " ledger=" + shown
                + " reason=" + reason);
    }

    private String runNumberOf(List<ReconTransaction> batch) {
        if (batch == null) {
            return "unknown";
        }
        for (ReconTransaction tx : batch) {
            if (tx.getRunId() != null) {
                return runRepository.findById(tx.getRunId())
                        .map(ReconRun::getRunNumber)
                        .orElse("unknown");
            }
        }
        return "unknown";
    }

    private static int promptRowCount(List<ReconTransaction> batch) {
        int count = 0;
        for (ReconTransaction tx : batch) {
            if (tx.getVoucherType() != VoucherType.CONTRA) {
                count++;
            }
        }
        return count;
    }

    private static Set<String> parsedPromptIds(List<LedgerMappingResult> mappings) {
        Set<String> ids = new HashSet<>();
        if (mappings == null) {
            return ids;
        }
        for (LedgerMappingResult mapping : mappings) {
            if (mapping.transactionId() != null && !mapping.transactionId().isBlank()) {
                ids.add(mapping.transactionId().trim());
            }
        }
        return ids;
    }

    private static List<ReconTransaction> unparsedTransactions(List<ReconTransaction> batch, Set<String> parsedIds) {
        List<ReconTransaction> leftover = new ArrayList<>();
        int promptId = 1;
        for (ReconTransaction tx : batch) {
            if (tx.getVoucherType() == VoucherType.CONTRA) {
                continue;
            }
            if (!parsedIds.contains(Integer.toString(promptId))) {
                leftover.add(tx);
            }
            promptId++;
        }
        return leftover;
    }

    @Transactional
    public TransactionResponse updateTransactionMapping(
            UUID runId, UUID txId, String ledgerName, String voucherType, Boolean isExcluded, String updatedBy) {
        ReconTransaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (!tx.getRunId().equals(runId)) {
            throw new IllegalArgumentException("Transaction does not belong to this run");
        }
        ReconRun run = runRepository.findById(tx.getRunId())
                .orElseThrow(() -> new IllegalArgumentException("Run not found"));
        if (run.getStatus() == ReconRunStatus.CLOSED) {
            throw new IllegalStateException("Cannot update transactions on a closed run");
        }
        String oldValue = snapshot(tx);
        if (voucherType != null && !voucherType.isBlank()) {
            tx.setVoucherType(VoucherType.valueOf(voucherType.toUpperCase(Locale.ROOT)));
        }
        if (isExcluded != null) {
            tx.setExcluded(isExcluded);
        }
        boolean ledgerChanged = false;
        if (ledgerName != null) {
            String trimmed = ledgerName.isBlank() ? null : ledgerName;
            ledgerChanged = !Objects.equals(trimmed, tx.getMappedLedger());
            tx.setMappedLedger(trimmed);
            if (trimmed != null) {
                tx.setMappingSource(MappingSource.MANUAL);
                upsertLearnedMapping(
                        tx.getNormalisedDescription(), tx.getVoucherType(), trimmed, tx.getAmount(), updatedBy);
            }
        } else if (ledgerChanged) {
            tx.setMappingSource(MappingSource.MANUAL);
        }
        tx.setReviewed(true);
        transactionRepository.save(tx);
        audit("recon_transaction", tx.getId(), "UPDATE", oldValue, snapshot(tx), updatedBy);
        refreshRunCounts(run, transactionRepository.findByRunIdOrderBySortOrderAsc(run.getId()));
        runRepository.save(run);
        return toTxResponse(tx);
    }

    @Transactional
    public ExportFile generateExport(UUID runId, List<UUID> transactionIds, String generatedBy) {
        ReconRun run = requireRun(runId);
        List<ReconTransaction> all = transactionRepository.findByRunIdOrderBySortOrderAsc(runId);
        Set<UUID> wanted = transactionIds == null || transactionIds.isEmpty()
                ? all.stream()
                .filter(t -> !t.isExcluded() && t.getMappedLedger() != null)
                .map(ReconTransaction::getId)
                .collect(Collectors.toSet())
                : new HashSet<>(transactionIds);
        List<ReconTransaction> selected = all.stream()
                .filter(t -> wanted.contains(t.getId()))
                .filter(t -> !t.isExcluded() && t.getMappedLedger() != null)
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No mapped, included transactions to export");
        }
        String bankLedger = run.getBankLedgerName();
        if (bankLedger == null || bankLedger.isBlank()) {
            throw new IllegalStateException(
                    "This run has no bank ledger. Map the statement account in Configuration → Account Mapping.");
        }
        int exportNumber = exportRepository.countByRunId(runId) + 1;
        TallyPrimeExcelExporter.ExportFile file = excelExporter.export(run, selected, bankLedger, exportNumber);
        ReconExport export = ReconExport.builder()
                .runId(runId)
                .exportNumber(exportNumber)
                .filename(file.filename())
                .transactionCount(selected.size())
                .generatedAt(Instant.now())
                .generatedBy(generatedBy)
                .build();
        exportRepository.save(export);
        run.setExportCount(exportRepository.countByRunId(runId));
        runRepository.save(run);
        return new ExportFile(file.bytes(), file.filename());
    }

    public record ExportFile(byte[] bytes, String filename) {}

    @Transactional
    public RunResponse closeRun(UUID runId, String closedBy) {
        ReconRun run = requireRun(runId);
        if (run.getStatus() == ReconRunStatus.CLOSED) {
            throw new IllegalStateException("Run is already closed");
        }
        List<ReconTransaction> txs = transactionRepository.findByRunIdOrderBySortOrderAsc(runId);
        boolean blocking = txs.stream().anyMatch(t -> !t.isExcluded() && t.getMappedLedger() == null);
        if (blocking) {
            throw new IllegalStateException("Map or exclude all remaining unmapped transactions before closing");
        }
        refreshRunCounts(run, txs);
        transactionRepository.deleteByRunId(runId);
        run.setStatus(ReconRunStatus.CLOSED);
        runRepository.save(run);
        audit("recon_run", runId, "CLOSE", ReconRunStatus.OPEN.name(), ReconRunStatus.CLOSED.name(), closedBy);
        return toRunResponse(run, List.of(), exportRepository.findByRunIdOrderByExportNumberAsc(runId));
    }

    @Transactional
    public void discardRun(UUID runId, String discardedBy) {
        ReconRun run = requireRun(runId);
        String snapshot = run.getRunNumber() + "|" + run.getStatus()
                + "|txs=" + transactionRepository.countByRunId(runId)
                + "|exports=" + exportRepository.countByRunId(runId);
        if (run.getStatus() == ReconRunStatus.OPEN) {
            transactionRepository.deleteByRunId(runId);
        }
        exportRepository.deleteByRunId(runId);
        audit("recon_run", runId, "DISCARD", snapshot, null, discardedBy);
        runRepository.delete(run);
    }

    public PageResponse<RunResponse> listRuns(int page, int size) {
        Page<ReconRun> result = runRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<RunResponse> content = result.getContent().stream()
                .map(run -> toRunResponse(run, List.of(),
                        exportRepository.findByRunIdOrderByExportNumberAsc(run.getId())))
                .toList();
        return new PageResponse<>(content, result.getTotalElements(), page, size);
    }

    public RunResponse getRun(UUID runId) {
        ReconRun run = requireRun(runId);
        List<ReconTransaction> txs = run.getStatus() == ReconRunStatus.OPEN
                ? transactionRepository.findByRunIdOrderBySortOrderAsc(runId)
                : List.of();
        return toRunResponse(run, txs, exportRepository.findByRunIdOrderByExportNumberAsc(runId));
    }

    @Transactional
    public ImportCountResponse importLedgerMaster(MultipartFile xmlFile) {
        TallyLedgerXmlParser.ParseResult parsed = xmlParser.parse(xmlFile);
        upsertCustomGroups(parsed.groups());
        int imported = 0;
        int updated = 0;
        for (TallyLedgerXmlParser.ParsedLedger item : parsed.ledgers()) {
            String parentGroup = item.parentGroup();
            String nature = parentGroup == null
                    ? null
                    : ledgerGroupRepository.findByGroupName(parentGroup)
                            .map(TallyLedgerGroup::getAccountingNature)
                            .orElse(null);
            if (nature == null) {
                nature = TallyAccountingNature.fromUnknownParent(parentGroup);
                log.warn("Ledger {} has empty or unknown Tally group {}; defaulting accounting_nature to {}",
                        item.name(), parentGroup, nature);
            }
            String groupName = parentGroup == null ? "" : parentGroup;
            boolean bank = "Bank Accounts".equals(parentGroup);
            Optional<TallyLedger> existing = ledgerRepository.findExistingForImport(item.name());
            if (existing.isPresent()) {
                TallyLedger ledger = existing.get();
                ledger.setLedgerName(item.name());
                ledger.setGroupName(groupName);
                ledger.setAccountingNature(nature);
                ledgerRepository.save(ledger);
                updated++;
            } else {
                ledgerRepository.save(TallyLedger.builder()
                        .ledgerName(item.name())
                        .groupName(groupName)
                        .accountingNature(nature)
                        .bankAccount(bank)
                        .active(true)
                        .createdAt(Instant.now())
                        .build());
                imported++;
            }
        }
        return new ImportCountResponse(imported, updated);
    }

    private void upsertCustomGroups(List<TallyLedgerXmlParser.ParsedGroup> groups) {
        Map<String, String> parentByGroup = new LinkedHashMap<>();
        for (TallyLedgerXmlParser.ParsedGroup group : groups) {
            parentByGroup.put(group.name(), group.parentGroup());
        }
        Map<String, String> natureByGroup = new HashMap<>();
        for (TallyLedgerGroup existing : ledgerGroupRepository.findAll()) {
            natureByGroup.put(existing.getGroupName(), existing.getAccountingNature());
        }
        for (Map.Entry<String, String> entry : parentByGroup.entrySet()) {
            String groupName = entry.getKey();
            String nature = resolveGroupNature(groupName, parentByGroup, natureByGroup);
            natureByGroup.put(groupName, nature);
            Optional<TallyLedgerGroup> existing = ledgerGroupRepository.findByGroupName(groupName);
            if (existing.isPresent()) {
                TallyLedgerGroup row = existing.get();
                if (!nature.equals(row.getAccountingNature())) {
                    row.setAccountingNature(nature);
                    ledgerGroupRepository.save(row);
                }
            } else {
                ledgerGroupRepository.save(TallyLedgerGroup.builder()
                        .groupName(groupName)
                        .accountingNature(nature)
                        .build());
            }
        }
    }

    static String resolveGroupNature(
            String groupName,
            Map<String, String> parentByGroup,
            Map<String, String> natureByGroup) {
        if (natureByGroup.containsKey(groupName)) {
            return natureByGroup.get(groupName);
        }
        Set<String> visited = new HashSet<>();
        visited.add(groupName);
        String cursor = parentByGroup.get(groupName);
        while (cursor != null && visited.add(cursor)) {
            if (natureByGroup.containsKey(cursor)) {
                return natureByGroup.get(cursor);
            }
            String next = parentByGroup.get(cursor);
            if (next == null) {
                return TallyAccountingNature.fromPattern(cursor)
                        .or(() -> TallyAccountingNature.fromPattern(groupName))
                        .orElse(TallyAccountingNature.LIABILITY);
            }
            cursor = next;
        }
        return TallyAccountingNature.fromPattern(groupName)
                .or(() -> TallyAccountingNature.fromPattern(parentByGroup.get(groupName)))
                .orElse(TallyAccountingNature.LIABILITY);
    }

    @Transactional
    public LedgerResponse addLedger(CreateLedgerRequest request) {
        if (request.ledgerName() == null || request.ledgerName().isBlank()) {
            throw new IllegalArgumentException("Ledger name is required");
        }
        if (request.groupName() == null || request.groupName().isBlank()) {
            throw new IllegalArgumentException("Group name is required");
        }
        if (ledgerRepository.existsByLedgerName(request.ledgerName().trim())) {
            throw new IllegalArgumentException("Ledger already exists: " + request.ledgerName());
        }
        TallyLedgerGroup group = ledgerGroupRepository.findByGroupName(request.groupName().trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown Tally group: " + request.groupName()));
        TallyLedger ledger = ledgerRepository.save(TallyLedger.builder()
                .ledgerName(request.ledgerName().trim())
                .groupName(group.getGroupName())
                .accountingNature(group.getAccountingNature())
                .bankAccount("Bank Accounts".equals(group.getGroupName()))
                .active(true)
                .createdAt(Instant.now())
                .build());
        return toLedger(ledger);
    }

    public PageResponse<LedgerResponse> listLedgers(String search, int page, int size) {
        return listLedgers(search, null, page, size, null, null);
    }

    public PageResponse<LedgerResponse> listLedgers(String search, VoucherType voucherType, int page, int size) {
        return listLedgers(search, voucherType, page, size, null, null);
    }

    public PageResponse<LedgerResponse> listLedgers(
            String search, VoucherType voucherType, int page, int size, String excludeLedger, Boolean hasHint) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("ledgerName"));
        String query = search != null && !search.isBlank() ? search.trim() : null;
        String exclude = excludeLedger != null && !excludeLedger.isBlank() ? excludeLedger.trim() : null;
        Page<TallyLedger> result = ledgerRepository.findAll(
                ledgerSpec(query, voucherType, exclude, hasHint), pageable);
        Set<UUID> hinted = new HashSet<>(ledgerHintRepository.findLedgerIds());
        List<LedgerResponse> content = result.getContent().stream()
                .map(ledger -> toLedger(ledger, hinted.contains(ledger.getId())))
                .toList();
        return new PageResponse<>(content, result.getTotalElements(), page, size);
    }

    private static Specification<TallyLedger> ledgerSpec(
            String search, VoucherType voucherType, String excludeLedger, Boolean hasHint) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (search != null) {
                preds.add(cb.like(cb.lower(root.get("ledgerName")), "%" + search.toLowerCase(Locale.ROOT) + "%"));
            }
            if (voucherType == VoucherType.CONTRA) {
                preds.add(cb.isTrue(root.get("bankAccount")));
            } else if (voucherType == VoucherType.PAYMENT) {
                preds.add(root.get("accountingNature").in("Liability", "Expense"));
            } else if (voucherType == VoucherType.RECEIPT) {
                preds.add(root.get("accountingNature").in("Asset", "Income"));
            }
            if (excludeLedger != null) {
                preds.add(cb.notEqual(root.get("ledgerName"), excludeLedger));
            }
            if (hasHint != null && query != null) {
                Subquery<Integer> hintRows = query.subquery(Integer.class);
                Root<TallyLedgerHint> hintRoot = hintRows.from(TallyLedgerHint.class);
                hintRows.select(cb.literal(1));
                hintRows.where(cb.equal(hintRoot.get("ledgerId"), root.get("id")));
                preds.add(Boolean.TRUE.equals(hasHint) ? cb.exists(hintRows) : cb.not(cb.exists(hintRows)));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(Predicate[]::new));
        };
    }

    public List<AccountMappingResponse> listAccountMappings() {
        return accountMappingRepository.findAllByOrderByStatementTypeAscIdentifierAsc().stream()
                .map(row -> toAccountMapping(row, null))
                .toList();
    }

    @Transactional
    public AccountMappingResponse createAccountMapping(AccountMappingRequest request, String createdBy) {
        ParsedAccountMapping parsed = parseAccountMapping(request, null);
        if (accountMappingRepository.existsByStatementTypeAndIdentifier(parsed.type(), parsed.identifier())) {
            throw new IllegalArgumentException(
                    "An account mapping already exists for this statement type and identifier");
        }
        ReconAccountMapping saved = accountMappingRepository.save(ReconAccountMapping.builder()
                .statementType(parsed.type())
                .identifier(parsed.identifier())
                .ledgerName(parsed.ledger().getLedgerName())
                .active(parsed.active())
                .createdAt(Instant.now())
                .createdBy(createdBy)
                .build());
        return toAccountMapping(saved, parsed.warning());
    }

    @Transactional
    public AccountMappingResponse updateAccountMapping(UUID id, AccountMappingRequest request) {
        ReconAccountMapping row = accountMappingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account mapping not found"));
        ParsedAccountMapping parsed = parseAccountMapping(request, row);
        if (accountMappingRepository.existsByStatementTypeAndIdentifier(parsed.type(), parsed.identifier())
                && !(row.getStatementType() == parsed.type() && row.getIdentifier().equals(parsed.identifier()))) {
            throw new IllegalArgumentException(
                    "An account mapping already exists for this statement type and identifier");
        }
        row.setStatementType(parsed.type());
        row.setIdentifier(parsed.identifier());
        row.setLedgerName(parsed.ledger().getLedgerName());
        row.setActive(parsed.active());
        return toAccountMapping(accountMappingRepository.save(row), parsed.warning());
    }

    @Transactional
    public void deleteAccountMapping(UUID id) {
        if (!accountMappingRepository.existsById(id)) {
            throw new IllegalArgumentException("Account mapping not found");
        }
        accountMappingRepository.deleteById(id);
    }

    public LedgerHintResponse getLedgerHint(UUID ledgerId) {
        requireLedger(ledgerId);
        return ledgerHintRepository.findByLedgerId(ledgerId)
                .map(this::toLedgerHint)
                .orElseGet(() -> emptyLedgerHint(ledgerId));
    }

    @Transactional
    public LedgerHintResponse saveLedgerHint(UUID ledgerId, LedgerHintRequest request, String updatedBy) {
        requireLedger(ledgerId);
        String purpose = hintField(request == null ? null : request.purpose(), 500, "Purpose");
        String keywords = hintField(request == null ? null : request.keywords(), 500, "Keywords");
        String typicalAmount = hintField(request == null ? null : request.typicalAmount(), 255, "Typical amount");
        String note = hintField(request == null ? null : request.disambiguationNote(), 500, "Disambiguation note");
        if (purpose == null && keywords == null && typicalAmount == null && note == null) {
            ledgerHintRepository.findByLedgerId(ledgerId).ifPresent(ledgerHintRepository::delete);
            return emptyLedgerHint(ledgerId);
        }
        TallyLedgerHint hint = ledgerHintRepository.findByLedgerId(ledgerId)
                .orElseGet(() -> TallyLedgerHint.builder().ledgerId(ledgerId).build());
        hint.setPurpose(purpose);
        hint.setKeywords(keywords);
        hint.setTypicalAmount(typicalAmount);
        hint.setDisambiguationNote(note);
        hint.setUpdatedBy(updatedBy);
        hint.setUpdatedAt(Instant.now());
        return toLedgerHint(ledgerHintRepository.save(hint));
    }

    public PageResponse<MappingResponse> listMappings(String search, int page, int size) {
        Page<LearnedMapping> result;
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastUsedAt"));
        if (search == null || search.isBlank()) {
            result = learnedMappingRepository.findAll(pr);
        } else {
            result = learnedMappingRepository
                    .findByNormalisedNarrationContainingIgnoreCaseOrLedgerNameContainingIgnoreCase(
                            search.trim(), search.trim(), pr);
        }
        return new PageResponse<>(result.getContent().stream().map(this::toMapping).toList(),
                result.getTotalElements(), page, size);
    }

    @Transactional
    public void deleteMapping(UUID id) {
        if (!learnedMappingRepository.existsById(id)) {
            throw new IllegalArgumentException("Mapping not found");
        }
        learnedMappingRepository.deleteById(id);
    }

    public List<HintResponse> listHints() {
        return hintRepository.findAll(Sort.by("createdAt")).stream().map(this::toHint).toList();
    }

    @Transactional
    public HintResponse createHint(HintRequest request, String createdBy) {
        if (request.hintText() == null || request.hintText().isBlank()) {
            throw new IllegalArgumentException("Hint text is required");
        }
        LlmHint hint = hintRepository.save(LlmHint.builder()
                .hintText(request.hintText().trim())
                .voucherType(request.voucherType() != null ? request.voucherType() : HintVoucherScope.ALL)
                .active(request.active() == null || request.active())
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .build());
        return toHint(hint);
    }

    @Transactional
    public HintResponse updateHint(UUID id, HintRequest request) {
        LlmHint hint = hintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hint not found"));
        if (request.hintText() != null) {
            hint.setHintText(request.hintText());
        }
        if (request.voucherType() != null) {
            hint.setVoucherType(request.voucherType());
        }
        if (request.active() != null) {
            hint.setActive(request.active());
        }
        return toHint(hintRepository.save(hint));
    }

    @Transactional
    public void deleteHint(UUID id) {
        hintRepository.deleteById(id);
    }

    public List<ContraRuleResponse> listContraRules() {
        return contraRuleRepository.findAll(Sort.by("createdAt")).stream().map(this::toContra).toList();
    }

    @Transactional
    public ContraRuleResponse createContraRule(ContraRuleRequest request) {
        if (request.patternType() == null || request.patternValue() == null || request.patternValue().isBlank()) {
            throw new IllegalArgumentException("Pattern type and value are required");
        }
        ContraRule rule = contraRuleRepository.save(ContraRule.builder()
                .patternType(request.patternType())
                .patternValue(request.patternValue().trim())
                .active(true)
                .createdAt(Instant.now())
                .build());
        return toContra(rule);
    }

    @Transactional
    public void deleteContraRule(UUID id) {
        contraRuleRepository.deleteById(id);
    }

    public OllamaConfigResponse getOllamaConfig() {
        boolean ollama = BankReconAiConfig.ollamaProvider(generalConfigService);
        LlmProviderSettings ollamaSettings = readOllamaSettings();
        LlmProviderSettings omlxSettings = readOmlxSettings();
        LlmProviderSettings active = ollama ? ollamaSettings : omlxSettings;
        return new OllamaConfigResponse(
                active.baseUrl(),
                active.chatModel(),
                active.embeddingUrl(),
                active.embeddingModel(),
                BankReconAiConfig.parseInt(generalConfigService.getConfigValue(CFG_BATCH).orElse("10"), 10),
                BankReconAiConfig.parseInt(
                        generalConfigService.getConfigValue(BankReconAiConfig.CFG_TIMEOUT).orElse("120"), 120),
                generalConfigService.getConfigValue(CFG_COMPANY).orElse("COGNOLOGIX"),
                ollama ? BankReconAiConfig.PROVIDER_OLLAMA : BankReconAiConfig.PROVIDER_OMLX,
                active.apiKey(),
                ollamaSettings,
                omlxSettings);
    }

    private LlmProviderSettings readOllamaSettings() {
        return new LlmProviderSettings(
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_BASE_URL)
                        .orElse(BankReconAiConfig.DEFAULT_OLLAMA_URL),
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_CHAT_MODEL)
                        .orElse(BankReconAiConfig.DEFAULT_OLLAMA_CHAT_MODEL),
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_EMBED_URL)
                        .orElse(BankReconAiConfig.DEFAULT_OLLAMA_URL),
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_EMBED_MODEL)
                        .orElse(BankReconAiConfig.DEFAULT_OLLAMA_EMBED_MODEL),
                "");
    }

    private LlmProviderSettings readOmlxSettings() {
        String baseUrl = generalConfigService.getConfigValue(BankReconAiConfig.CFG_MLX_BASE_URL)
                .orElse(BankReconAiConfig.DEFAULT_CHAT_URL);
        return new LlmProviderSettings(
                baseUrl,
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_MLX_CHAT_MODEL)
                        .orElse(BankReconAiConfig.DEFAULT_CHAT_MODEL),
                baseUrl,
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_MLX_EMBED_MODEL)
                        .orElse(BankReconAiConfig.DEFAULT_OMLX_EMBED_MODEL),
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_API_KEY).orElse(""));
    }

    @Transactional
    public OllamaConfigResponse updateOllamaConfig(OllamaConfigRequest request) {
        String provider = request.provider() == null
                ? (BankReconAiConfig.ollamaProvider(generalConfigService)
                        ? BankReconAiConfig.PROVIDER_OLLAMA
                        : BankReconAiConfig.PROVIDER_OMLX)
                : request.provider().trim().toUpperCase(Locale.ROOT);
        if (!BankReconAiConfig.PROVIDER_OLLAMA.equals(provider)
                && !BankReconAiConfig.PROVIDER_OMLX.equals(provider)) {
            throw new IllegalArgumentException("llm_provider must be OLLAMA or OMLX");
        }
        generalConfigService.setConfigValue(BankReconAiConfig.CFG_PROVIDER, provider);
        if (BankReconAiConfig.PROVIDER_OLLAMA.equals(provider)) {
            if (request.baseUrl() != null) {
                generalConfigService.setConfigValue(BankReconAiConfig.CFG_BASE_URL, request.baseUrl().trim());
            }
            if (request.chatModel() != null) {
                generalConfigService.setConfigValue(BankReconAiConfig.CFG_CHAT_MODEL, request.chatModel().trim());
            }
            if (request.embeddingUrl() != null) {
                generalConfigService.setConfigValue(BankReconAiConfig.CFG_EMBED_URL, request.embeddingUrl().trim());
            }
            if (request.embeddingModel() != null) {
                generalConfigService.setConfigValue(BankReconAiConfig.CFG_EMBED_MODEL, request.embeddingModel().trim());
            }
        } else {
            if (request.baseUrl() != null) {
                generalConfigService.setConfigValue(BankReconAiConfig.CFG_MLX_BASE_URL, request.baseUrl().trim());
            }
            if (request.chatModel() != null) {
                generalConfigService.setConfigValue(BankReconAiConfig.CFG_MLX_CHAT_MODEL, request.chatModel().trim());
            }
            if (request.embeddingModel() != null) {
                generalConfigService.setConfigValue(BankReconAiConfig.CFG_MLX_EMBED_MODEL, request.embeddingModel().trim());
            }
            if (request.apiKey() != null) {
                generalConfigService.setConfigValue(BankReconAiConfig.CFG_API_KEY, request.apiKey().trim());
            }
        }
        if (request.batchSize() != null) {
            generalConfigService.setConfigValue(CFG_BATCH, String.valueOf(request.batchSize()));
        }
        if (request.timeoutSeconds() != null) {
            generalConfigService.setConfigValue(BankReconAiConfig.CFG_TIMEOUT, String.valueOf(request.timeoutSeconds()));
        }
        if (request.companyName() != null) {
            generalConfigService.setConfigValue(CFG_COMPANY, request.companyName().trim());
        }
        return getOllamaConfig();
    }

    public OllamaTestResponse testOllamaConnection(String target, OllamaConfigRequest draft) {
        boolean wantChat = target == null || target.isBlank() || "chat".equalsIgnoreCase(target);
        boolean wantEmbedding = target == null || target.isBlank() || "embedding".equalsIgnoreCase(target);
        if (!wantChat && !wantEmbedding) {
            throw new IllegalArgumentException("target must be chat or embedding");
        }
        OllamaConfigResponse cfg = draft == null ? getOllamaConfig() : configForTest(draft);
        String chatMessage = null;
        String embedMessage = null;
        boolean chatOk = false;
        boolean embedOk = false;
        if (wantChat) {
            try {
                probeChat(cfg);
                chatOk = true;
                chatMessage = "Chat model responded.";
            } catch (Exception e) {
                chatMessage = e.getMessage();
            }
        }
        if (wantEmbedding) {
            try {
                probeEmbedding(cfg);
                embedOk = true;
                embedMessage = "Embedding model responded.";
            } catch (Exception e) {
                embedMessage = e.getMessage();
            }
        }
        boolean connected = (!wantChat || chatOk) && (!wantEmbedding || embedOk);
        String message = wantChat && wantEmbedding
                ? joinMessages(chatMessage, embedMessage)
                : wantChat ? chatMessage : embedMessage;
        return new OllamaTestResponse(connected, List.of(), chatOk, embedOk, message);
    }

    private OllamaConfigResponse configForTest(OllamaConfigRequest draft) {
        OllamaConfigResponse saved = getOllamaConfig();
        String provider = draft.provider() == null || draft.provider().isBlank()
                ? saved.provider()
                : draft.provider().trim().toUpperCase(Locale.ROOT);
        boolean omlx = BankReconAiConfig.PROVIDER_OMLX.equals(provider);
        String baseUrl = firstText(draft.baseUrl(), saved.baseUrl());
        String embeddingUrl = omlx ? baseUrl : firstText(draft.embeddingUrl(), saved.embeddingUrl());
        String apiKey = draft.apiKey() != null ? draft.apiKey().trim() : saved.apiKey();
        return new OllamaConfigResponse(
                baseUrl,
                firstText(draft.chatModel(), saved.chatModel()),
                embeddingUrl,
                firstText(draft.embeddingModel(), saved.embeddingModel()),
                saved.batchSize(),
                saved.timeoutSeconds(),
                saved.companyName(),
                provider,
                apiKey,
                saved.ollamaSettings(),
                saved.omlxSettings());
    }

    private static String firstText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback;
    }

    private static String joinMessages(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank() || first.equals(second)) {
            return first;
        }
        return first + "\n" + second;
    }

    private static void probeChat(OllamaConfigResponse cfg) {
        if (omlx(cfg)) {
            jsonPost(joinUrl(cfg.baseUrl(), "/v1/chat/completions"), cfg.apiKey())
                    .body(Map.of(
                            "model", cfg.chatModel(),
                            "messages", List.of(Map.of("role", "user", "content", "ping")),
                            "max_tokens", 1,
                            "temperature", 0))
                    .retrieve()
                    .toBodilessEntity();
            return;
        }
        jsonPost(joinUrl(cfg.baseUrl(), "/api/chat"), null)
                .body(Map.of(
                        "model", cfg.chatModel(),
                        "messages", List.of(Map.of("role", "user", "content", "ping")),
                        "stream", false))
                .retrieve()
                .toBodilessEntity();
    }

    private static void probeEmbedding(OllamaConfigResponse cfg) {
        if (omlx(cfg)) {
            jsonPost(joinUrl(cfg.baseUrl(), "/v1/embeddings"), cfg.apiKey())
                    .body(Map.of("model", cfg.embeddingModel(), "input", "ping"))
                    .retrieve()
                    .toBodilessEntity();
            return;
        }
        jsonPost(joinUrl(cfg.embeddingUrl(), "/api/embeddings"), null)
                .body(Map.of("model", cfg.embeddingModel(), "prompt", "ping"))
                .retrieve()
                .toBodilessEntity();
    }

    private static boolean omlx(OllamaConfigResponse cfg) {
        return BankReconAiConfig.PROVIDER_OMLX.equalsIgnoreCase(cfg.provider());
    }

    private static RestClient.RequestBodySpec jsonPost(String url, String apiKey) {
        if (apiKey != null && apiKey.isBlank()) {
            throw new IllegalStateException("API key is required");
        }
        RestClient.RequestBodySpec spec = probeClient().post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (apiKey != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        }
        return spec;
    }

    private static RestClient probeClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static String joinUrl(String baseUrl, String path) {
        String root = baseUrl == null ? "" : baseUrl.trim();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (root.endsWith(path)) {
            return root;
        }
        if (path.startsWith("/v1/") && root.endsWith("/v1")) {
            return root + path.substring(3);
        }
        return root + path;
    }

    @Transactional
    public MigrateResponse migrateLearnings(MultipartFile csvFile) {
        List<List<String>> rows = readCsv(csvFile);
        if (rows.isEmpty()) {
            return new MigrateResponse(0, 0);
        }
        Map<String, Integer> header = headerIndex(rows.get(0));
        int migrated = 0;
        int skipped = 0;
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String narration = cell(row, header, "normalised_narration");
            String type = cell(row, header, "voucher_type");
            String ledger = cell(row, header, "ledger_name");
            if (narration.isBlank() || type.isBlank() || ledger.isBlank()) {
                skipped++;
                continue;
            }
            if (!ledgerRepository.existsByLedgerName(ledger)) {
                skipped++;
                continue;
            }
            VoucherType voucherType = VoucherType.valueOf(type.toUpperCase(Locale.ROOT));
            LearnedMapping mapping = learnedMappingRepository
                    .findByNormalisedNarrationAndVoucherType(narration, voucherType)
                    .orElseGet(() -> LearnedMapping.builder()
                            .normalisedNarration(narration)
                            .voucherType(voucherType)
                            .createdAt(Instant.now())
                            .build());
            mapping.setLedgerName(ledger);
            mapping.setUseCount(parseCount(cell(row, header, "use_count"), mapping.getUseCount()));
            mapping.setLastUsedAt(parseInstant(cell(row, header, "last_used_at")));
            mapping = learnedMappingRepository.save(mapping);
            persistEmbedding(mapping.getId(), narration);
            migrated++;
        }
        return new MigrateResponse(migrated, skipped);
    }

    @Transactional
    public MigrateResponse migrateRunHistory(MultipartFile csvFile) {
        List<List<String>> rows = readCsv(csvFile);
        if (rows.isEmpty()) {
            return new MigrateResponse(0, 0);
        }
        Map<String, Integer> header = headerIndex(rows.get(0));
        int migrated = 0;
        int skipped = 0;
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String runNumber = cell(row, header, "run_number");
            if (runNumber.isBlank() || runRepository.findByRunNumber(runNumber).isPresent()) {
                skipped++;
                continue;
            }
            ReconRun run = ReconRun.builder()
                    .runNumber(runNumber)
                    .statementNumber(blankToNull(cell(row, header, "statement_number")))
                    .accountNumber(blankToNull(cell(row, header, "account_number")))
                    .statementPeriodStart(parseLocalDate(cell(row, header, "statement_period_start")))
                    .statementPeriodEnd(parseLocalDate(cell(row, header, "statement_period_end")))
                    .originalFilename(blankToNull(cell(row, header, "original_filename")))
                    .totalTransactions(parseCount(cell(row, header, "total_transactions"), 0))
                    .mappedCount(parseCount(cell(row, header, "mapped_count"), 0))
                    .unmappedCount(parseCount(cell(row, header, "unmapped_count"), 0))
                    .excludedCount(parseCount(cell(row, header, "excluded_count"), 0))
                    .exportCount(parseCount(cell(row, header, "export_count"), 0))
                    .status("OPEN".equalsIgnoreCase(cell(row, header, "status"))
                            ? ReconRunStatus.OPEN : ReconRunStatus.CLOSED)
                    .createdAt(parseInstant(cell(row, header, "created_at")))
                    .createdBy(blankToNull(cell(row, header, "created_by")))
                    .build();
            runRepository.save(run);
            migrated++;
        }
        return new MigrateResponse(migrated, skipped);
    }

    private void upsertLearnedMapping(
            String narration, VoucherType voucherType, String ledger, BigDecimal amount, String by) {
        LearnedMapping mapping = learnedMappingRepository
                .findByNormalisedNarrationAndVoucherType(narration, voucherType)
                .orElseGet(() -> LearnedMapping.builder()
                        .normalisedNarration(narration)
                        .voucherType(voucherType)
                        .createdAt(Instant.now())
                        .createdBy(by)
                        .build());
        mapping.setLedgerName(ledger);
        mapping.setUseCount(mapping.getId() == null ? 1 : mapping.getUseCount() + 1);
        mapping.setLastUsedAt(Instant.now());
        if (amount != null) {
            mapping.setLastAmount(amount.setScale(2, RoundingMode.HALF_UP));
        }
        mapping = learnedMappingRepository.save(mapping);
        persistEmbedding(mapping.getId(), narration);
    }

    private void persistEmbedding(UUID mappingId, String narration) {
        float[] embedding = narrationEmbedder.embed(narration);
        if (embedding != null && embedding.length > 0) {
            try {
                vectorStore.saveEmbedding(mappingId, embedding);
            } catch (Exception e) {
                log.warn("Could not store narration embedding: {}", e.getMessage());
            }
        }
    }

    String buildPrompt(List<ReconTransaction> batch) {
        List<MappingPrompt.HintLine> hints = hintRepository.findByActiveTrue().stream()
                .sorted(Comparator.comparing((LlmHint hint) -> hint.getVoucherType().name())
                        .thenComparing(LlmHint::getHintText, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(hint -> hint.getId() == null ? "" : hint.getId().toString()))
                .map(hint -> new MappingPrompt.HintLine(hint.getVoucherType().name(), hint.getHintText()))
                .toList();
        List<MappingPrompt.LedgerLine> payment = catalogLines(VoucherType.PAYMENT);
        List<MappingPrompt.LedgerLine> receipt = catalogLines(VoucherType.RECEIPT);
        List<LearnedMapping> learned = learnedMappingRepository.findAll();
        List<MappingPrompt.TransactionLine> transactions = new ArrayList<>();
        int promptId = 1;
        for (ReconTransaction tx : batch) {
            if (tx.getVoucherType() == VoucherType.CONTRA) {
                continue;
            }
            List<MappingPrompt.ExampleLine> examples = examplesFor(tx, learned).stream()
                    .map(example -> new MappingPrompt.ExampleLine(
                            example.getNormalisedNarration(),
                            plainAmount(example.getLastAmount()),
                            example.getLedgerName()))
                    .toList();
            transactions.add(new MappingPrompt.TransactionLine(
                    Integer.toString(promptId++),
                    tx.getDescription(),
                    plainAmount(tx.getAmount()),
                    tx.getVoucherType().name(),
                    examples));
        }
        return MappingPrompt.build(hints, payment, receipt, transactions);
    }

    private List<MappingPrompt.LedgerLine> catalogLines(VoucherType type) {
        Set<String> mappedNames = accountMappingRepository.findAll().stream()
                .map(ReconAccountMapping::getLedgerName)
                .collect(Collectors.toSet());
        Map<UUID, TallyLedgerHint> hintsByLedger = ledgerHintRepository.findAll().stream()
                .collect(Collectors.toMap(TallyLedgerHint::getLedgerId, hint -> hint, (a, b) -> a));
        return ledgerRepository.findByActiveTrueOrderByLedgerNameAsc().stream()
                .filter(ledger -> catalogNature(type, ledger))
                .filter(ledger -> !MappingPrompt.excludedFromCatalog(
                        ledger.getGroupName(), ledger.getLedgerName(), mappedNames))
                .sorted(Comparator.comparing(TallyLedger::getGroupName, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(TallyLedger::getLedgerName, Comparator.nullsFirst(String::compareTo)))
                .map(ledger -> {
                    TallyLedgerHint hint = hintsByLedger.get(ledger.getId());
                    return new MappingPrompt.LedgerLine(
                            ledger.getGroupName(),
                            ledger.getLedgerName(),
                            hint == null ? null : hint.getKeywords(),
                            hint == null ? null : hint.getPurpose(),
                            hint == null ? null : hint.getTypicalAmount(),
                            hint == null ? null : hint.getDisambiguationNote());
                })
                .toList();
    }

    private static boolean catalogNature(VoucherType type, TallyLedger ledger) {
        return switch (type) {
            case PAYMENT -> "Liability".equals(ledger.getAccountingNature())
                    || "Expense".equals(ledger.getAccountingNature());
            case RECEIPT -> "Asset".equals(ledger.getAccountingNature())
                    || "Income".equals(ledger.getAccountingNature());
            case CONTRA -> false;
        };
    }

    private List<LearnedMapping> examplesFor(ReconTransaction tx, List<LearnedMapping> learned) {
        List<LearnedMapping> examples = topFuzzy(tx, learned);
        if (examples.isEmpty() || maxScore(tx, examples) < FUZZY_THRESHOLD) {
            examples = semanticFallback(tx);
        }
        return examples.stream().limit(MappingPrompt.EXAMPLE_LIMIT).toList();
    }

    private List<LearnedMapping> topFuzzy(ReconTransaction tx, List<LearnedMapping> learned) {
        return learned.stream()
                .filter(m -> m.getVoucherType() == tx.getVoucherType())
                .sorted(Comparator.comparingDouble(
                        (LearnedMapping m) -> FuzzyMappingScorer.score(tx.getNormalisedDescription(),
                                m.getNormalisedNarration())).reversed())
                .limit(MappingPrompt.EXAMPLE_LIMIT)
                .toList();
    }

    private double maxScore(ReconTransaction tx, List<LearnedMapping> examples) {
        return examples.stream()
                .mapToDouble(m -> FuzzyMappingScorer.score(tx.getNormalisedDescription(), m.getNormalisedNarration()))
                .max()
                .orElse(0);
    }

    private List<LearnedMapping> semanticFallback(ReconTransaction tx) {
        float[] embedding = narrationEmbedder.embed(tx.getNormalisedDescription());
        if (embedding.length == 0) {
            return List.of();
        }
        try {
            List<LearnedMappingVectorStore.SimilarMapping> similar =
                    vectorStore.similaritySearch(embedding, tx.getVoucherType().name(), MappingPrompt.EXAMPLE_LIMIT);
            return similar.stream()
                    .map(s -> learnedMappingRepository.findById(s.id()).orElse(null))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.debug("Vector similarity search skipped: {}", e.getMessage());
            return List.of();
        }
    }

    private Set<String> allowedLedgers(VoucherType type) {
        return catalogLines(type).stream()
                .map(MappingPrompt.LedgerLine::ledgerName)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String requireBankLedger(String accountNumber) {
        return accountMappingRepository
                .findByStatementTypeAndIdentifierAndActiveTrue(StatementType.HDFC_BANK, accountNumber)
                .map(ReconAccountMapping::getLedgerName)
                .orElseThrow(() -> new UnmappedAccountException(
                        "No ledger is mapped for account " + accountNumber
                                + ". Add it in Configuration → Account Mapping."));
    }

    private ParsedAccountMapping parseAccountMapping(AccountMappingRequest request, ReconAccountMapping existing) {
        if (request == null || request.statementType() == null || request.statementType().isBlank()) {
            throw new IllegalArgumentException("Statement type is required");
        }
        StatementType type;
        try {
            type = StatementType.valueOf(request.statementType().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Statement type must be HDFC_BANK or HSBC_CC");
        }
        if (request.identifier() == null || request.identifier().isBlank()) {
            throw new IllegalArgumentException("Identifier is required");
        }
        String identifier = request.identifier().trim();
        if (identifier.length() > 100) {
            throw new IllegalArgumentException("Identifier must be at most 100 characters");
        }
        if (request.ledgerName() == null || request.ledgerName().isBlank()) {
            throw new IllegalArgumentException("Ledger name is required");
        }
        String ledgerName = request.ledgerName().trim();
        TallyLedger ledger = ledgerRepository.findByLedgerName(ledgerName)
                .orElseThrow(() -> new IllegalArgumentException("Ledger not found: " + ledgerName));
        boolean active = request.active() == null ? existing == null || existing.isActive() : request.active();
        String warning = MappingPrompt.isBankSideGroup(ledger.getGroupName())
                ? null
                : "Ledger \"" + ledger.getLedgerName() + "\" is in group " + ledger.getGroupName()
                        + ", not Bank Accounts, Credit Cards, or Bank OD.";
        return new ParsedAccountMapping(type, identifier, ledger, active, warning);
    }

    private TallyLedger requireLedger(UUID ledgerId) {
        return ledgerRepository.findById(ledgerId)
                .orElseThrow(() -> new IllegalArgumentException("Ledger not found"));
    }

    private AccountMappingResponse toAccountMapping(ReconAccountMapping row, String warning) {
        return new AccountMappingResponse(
                row.getId(),
                row.getStatementType().name(),
                row.getIdentifier(),
                row.getLedgerName(),
                row.isActive(),
                row.getCreatedAt(),
                row.getCreatedBy(),
                warning);
    }

    private LedgerHintResponse toLedgerHint(TallyLedgerHint hint) {
        return new LedgerHintResponse(
                hint.getLedgerId(),
                hint.getPurpose(),
                hint.getKeywords(),
                hint.getTypicalAmount(),
                hint.getDisambiguationNote(),
                true);
    }

    private static LedgerHintResponse emptyLedgerHint(UUID ledgerId) {
        return new LedgerHintResponse(ledgerId, null, null, null, null, false);
    }

    private static String plainAmount(BigDecimal amount) {
        return amount == null ? "" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record ParsedAccountMapping(
            StatementType type,
            String identifier,
            TallyLedger ledger,
            boolean active,
            String warning) {}

    private String nextRunNumber() {
        return String.format("R-%03d", runRepository.findMaxRunSequence() + 1);
    }

    private ReconRun requireRun(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found"));
    }

    private void refreshRunCounts(ReconRun run, List<ReconTransaction> txs) {
        int excluded = (int) txs.stream().filter(ReconTransaction::isExcluded).count();
        int mapped = (int) txs.stream().filter(t -> !t.isExcluded() && t.getMappedLedger() != null).count();
        int unmapped = (int) txs.stream().filter(t -> !t.isExcluded() && t.getMappedLedger() == null).count();
        run.setTotalTransactions(txs.size());
        run.setExcludedCount(excluded);
        run.setMappedCount(mapped);
        run.setUnmappedCount(unmapped);
    }

    private void audit(String type, UUID id, String action, String oldValue, String newValue, String by) {
        auditLogRepository.save(ReconAuditLog.builder()
                .entityType(type)
                .entityId(id)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(by)
                .changedAt(Instant.now())
                .build());
    }

    private static String snapshot(ReconTransaction tx) {
        return tx.getVoucherType() + "|" + tx.getMappedLedger() + "|" + tx.isExcluded();
    }

    private RunResponse toRunResponse(ReconRun run, List<ReconTransaction> txs, List<ReconExport> exports) {
        return new RunResponse(
                run.getId(), run.getRunNumber(), run.getStatementNumber(), run.getAccountNumber(),
                run.getBankLedgerName(),
                run.getCustomerName(),
                run.getStatementPeriodStart(), run.getStatementPeriodEnd(),
                run.getOpeningBalance(), run.getClosingBalance(),
                run.getOriginalFilename(),
                run.getTotalTransactions(), run.getMappedCount(), run.getUnmappedCount(),
                run.getExcludedCount(), run.getExportCount(), run.getStatus(), run.getCreatedAt(),
                run.getCreatedBy(), txs.stream().map(this::toTxResponse).toList(),
                exports.stream().map(e -> new ExportResponse(e.getId(), e.getExportNumber(), e.getFilename(),
                        e.getTransactionCount(), e.getGeneratedAt(), e.getGeneratedBy())).toList());
    }

    private TransactionResponse toTxResponse(ReconTransaction tx) {
        return new TransactionResponse(
                tx.getId(), tx.getTransactionDate(), tx.getDescription(), tx.getNormalisedDescription(),
                tx.getAmount(), tx.getDebitCredit(), tx.getReferenceNo(), tx.getValueDate(),
                tx.getTransactionBranch(), tx.getRunningBalance(), tx.getVoucherType(),
                tx.getMappedLedger(), tx.getMappingSource(), tx.isExcluded(), tx.isReviewed(), tx.getSortOrder());
    }

    private LedgerResponse toLedger(TallyLedger ledger) {
        return toLedger(ledger, false);
    }

    private LedgerResponse toLedger(TallyLedger ledger, boolean hasHint) {
        return new LedgerResponse(ledger.getId(), ledger.getLedgerName(), ledger.getGroupName(),
                ledger.getAccountingNature(), ledger.isBankAccount(), ledger.isActive(), hasHint);
    }

    private MappingResponse toMapping(LearnedMapping m) {
        return new MappingResponse(m.getId(), m.getNormalisedNarration(), m.getVoucherType(),
                m.getLedgerName(), m.getUseCount(), m.getLastUsedAt());
    }

    private HintResponse toHint(LlmHint h) {
        return new HintResponse(h.getId(), h.getHintText(), h.getVoucherType(), h.isActive(), h.getCreatedAt());
    }

    private ContraRuleResponse toContra(ContraRule r) {
        return new ContraRuleResponse(r.getId(), r.getPatternType(), r.getPatternValue(), r.isActive());
    }

    private List<List<String>> readCsv(MultipartFile file) {
        try {
            String text = new String(file.getBytes());
            List<List<String>> rows = new ArrayList<>();
            for (String line : text.split("\\r?\\n")) {
                if (!line.isBlank()) {
                    rows.add(HdfcStatementParser.parseCsvLine(line));
                }
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read CSV", e);
        }
    }

    private static Map<String, Integer> headerIndex(List<String> header) {
        java.util.HashMap<String, Integer> map = new java.util.HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            map.put(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return map;
    }

    private static String cell(List<String> row, Map<String, Integer> header, String key) {
        Integer idx = header.get(key);
        if (idx == null || idx >= row.size()) {
            return "";
        }
        return row.get(idx) == null ? "" : row.get(idx).trim();
    }

    private static String hintField(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + " must be at most " + max + " characters");
        }
        return trimmed;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static int parseCount(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception e) {
            LocalDate d = parseLocalDate(raw);
            return d != null ? d.atStartOfDay().toInstant(java.time.ZoneOffset.UTC) : Instant.now();
        }
    }

    private static LocalDate parseLocalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), ISO_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private MappingTemplateApi resolveColumnMapping(UUID mappingId) {
        if (mappingId != null) {
            MappingTemplateApi mapping = peoplePayrollService.findMappingApiById(mappingId)
                    .orElseThrow(() -> new IllegalArgumentException("Column mapping template not found"));
            if (!HDFC_IMPORT_TYPE.equals(mapping.importType())) {
                throw new IllegalArgumentException("Mapping template is not HDFC_BANK_STATEMENT");
            }
            return mapping;
        }
        return peoplePayrollService.findActiveMappingApi(HDFC_IMPORT_TYPE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active column mapping template for HDFC_BANK_STATEMENT"));
    }

    private Map<String, String> toExcelColumnMap(MappingTemplateApi mapping) {
        Map<String, String> excelToAttr = new LinkedHashMap<>();
        for (MappingTemplateApi.MappingLineApi line : mapping.lines()) {
            excelToAttr.putIfAbsent(line.excelColumnName(), line.systemAttribute());
        }
        return excelToAttr;
    }

    private static void validateMappingLines(List<PeoplePayrollService.MappingLineInput> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Map at least one column");
        }
        Set<String> known = new HashSet<>();
        known.addAll(BankStatementSystemAttribute.HEADER_ATTRIBUTES);
        known.addAll(BankStatementSystemAttribute.TRANSACTION_ATTRIBUTES);
        for (PeoplePayrollService.MappingLineInput line : lines) {
            if (line.systemAttribute() == null || !known.contains(line.systemAttribute())) {
                throw new IllegalArgumentException("Unknown system attribute: " + line.systemAttribute());
            }
        }
    }

}

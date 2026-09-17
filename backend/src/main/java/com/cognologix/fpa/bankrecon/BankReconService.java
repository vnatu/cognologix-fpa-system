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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
        ReconRun run = ReconRun.builder()
                .runNumber(nextRunNumber())
                .statementNumber(parsed.statementNumber())
                .accountNumber(parsed.accountNumber())
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
        String desc = tx.getNormalisedDescription() != null
                ? tx.getNormalisedDescription()
                : NarrationNormalizer.normalise(tx.getDescription());
        String company = generalConfigService.getConfigValue(CFG_COMPANY).orElse("COGNOLOGIX")
                .toUpperCase(Locale.ROOT);
        boolean companyMatch = desc.toUpperCase(Locale.ROOT).contains(company);
        if (companyMatch) {
            for (ContraRule rule : contraRules) {
                if (!rule.isActive()) {
                    continue;
                }
                String pattern = rule.getPatternValue() == null
                        ? ""
                        : rule.getPatternValue().toUpperCase(Locale.ROOT);
                String haystack = desc.toUpperCase(Locale.ROOT);
                boolean matches = rule.getPatternType() == PatternType.PREFIX
                        ? haystack.startsWith(pattern)
                        : haystack.contains(pattern);
                if (matches) {
                    return VoucherType.CONTRA;
                }
            }
        }
        return tx.getDebitCredit() == DebitCredit.D ? VoucherType.PAYMENT : VoucherType.RECEIPT;
    }

    public void runMappingPipeline(List<ReconTransaction> transactions) {
        List<ReconTransaction> unmapped = new ArrayList<>();
        for (ReconTransaction tx : transactions) {
            if (tx.isExcluded()) {
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
    }

    public void mapBatchWithLlm(List<ReconTransaction> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        String prompt = buildPrompt(batch);
        BatchMappingResponse response = structuredLlmClient.mapBatch(prompt);
        Map<String, String> byId = response.mappings() == null
                ? Map.of()
                : response.mappings().stream()
                .filter(m -> m.transactionId() != null)
                .collect(Collectors.toMap(LedgerMappingResult::transactionId,
                        LedgerMappingResult::ledgerName, (a, b) -> a));
        for (ReconTransaction tx : batch) {
            Set<String> allowed = allowedLedgers(tx.getVoucherType());
            String ledger = byId.get(tx.getId().toString());
            if (ledger != null && allowed.contains(ledger)) {
                tx.setMappedLedger(ledger);
                tx.setMappingSource(MappingSource.LLM);
            } else {
                tx.setMappedLedger(null);
                tx.setMappingSource(null);
            }
        }
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
                upsertLearnedMapping(tx.getNormalisedDescription(), tx.getVoucherType(), trimmed, updatedBy);
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
        String bankLedger = ledgerRepository.findByActiveTrueAndBankAccountTrue().stream()
                .findFirst()
                .map(TallyLedger::getLedgerName)
                .orElse("HDFC Bank");
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
                ledger.setBankAccount(bank);
                ledger.setActive(true);
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
        return listLedgers(search, null, page, size);
    }

    public PageResponse<LedgerResponse> listLedgers(String search, VoucherType voucherType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("ledgerName"));
        boolean hasSearch = search != null && !search.isBlank();
        String query = hasSearch ? search.trim() : null;
        Page<TallyLedger> result;
        if (voucherType == VoucherType.CONTRA) {
            result = hasSearch
                    ? ledgerRepository.findByBankAccountTrueAndLedgerNameContainingIgnoreCase(query, pageable)
                    : ledgerRepository.findByBankAccountTrue(pageable);
        } else if (voucherType == VoucherType.PAYMENT) {
            List<String> natures = List.of("Liability", "Expense");
            result = hasSearch
                    ? ledgerRepository.findByAccountingNatureInAndLedgerNameContainingIgnoreCase(
                            natures, query, pageable)
                    : ledgerRepository.findByAccountingNatureIn(natures, pageable);
        } else if (voucherType == VoucherType.RECEIPT) {
            List<String> natures = List.of("Asset", "Income");
            result = hasSearch
                    ? ledgerRepository.findByAccountingNatureInAndLedgerNameContainingIgnoreCase(
                            natures, query, pageable)
                    : ledgerRepository.findByAccountingNatureIn(natures, pageable);
        } else if (hasSearch) {
            result = ledgerRepository.findByLedgerNameContainingIgnoreCase(query, pageable);
        } else {
            result = ledgerRepository.findAll(pageable);
        }
        return new PageResponse<>(result.getContent().stream().map(this::toLedger).toList(),
                result.getTotalElements(), page, size);
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
        return new OllamaConfigResponse(
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_BASE_URL).orElse("http://localhost:11434"),
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_CHAT_MODEL).orElse("qwen2.5:32b"),
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_EMBED_MODEL).orElse("nomic-embed-text"),
                BankReconAiConfig.parseInt(generalConfigService.getConfigValue(CFG_BATCH).orElse("10"), 10),
                BankReconAiConfig.parseInt(
                        generalConfigService.getConfigValue(BankReconAiConfig.CFG_TIMEOUT).orElse("120"), 120),
                generalConfigService.getConfigValue(CFG_COMPANY).orElse("COGNOLOGIX"));
    }

    @Transactional
    public OllamaConfigResponse updateOllamaConfig(OllamaConfigRequest request) {
        if (request.baseUrl() != null) {
            generalConfigService.setConfigValue(BankReconAiConfig.CFG_BASE_URL, request.baseUrl().trim());
        }
        if (request.chatModel() != null) {
            generalConfigService.setConfigValue(BankReconAiConfig.CFG_CHAT_MODEL, request.chatModel().trim());
        }
        if (request.embeddingModel() != null) {
            generalConfigService.setConfigValue(BankReconAiConfig.CFG_EMBED_MODEL, request.embeddingModel().trim());
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

    public OllamaTestResponse testOllamaConnection() {
        OllamaConfigResponse cfg = getOllamaConfig();
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(5));
            factory.setReadTimeout(Duration.ofSeconds(10));
            RestClient client = RestClient.builder().baseUrl(cfg.baseUrl()).requestFactory(factory).build();
            TagsResponse tags = client.get().uri("/api/tags").retrieve().body(TagsResponse.class);
            List<String> models = tags == null || tags.models() == null
                    ? List.of()
                    : tags.models().stream().map(TagsModel::name).filter(Objects::nonNull).toList();
            boolean chat = models.stream().anyMatch(n -> n.startsWith(cfg.chatModel()));
            boolean embed = models.stream().anyMatch(n -> n.startsWith(cfg.embeddingModel()));
            return new OllamaTestResponse(true, models, chat, embed,
                    chat && embed ? "Connected. Chat and embedding models are available."
                            : "Connected, but required models may not be pulled.");
        } catch (Exception e) {
            return new OllamaTestResponse(false, List.of(), false, false, e.getMessage());
        }
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

    private void upsertLearnedMapping(String narration, VoucherType voucherType, String ledger, String by) {
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

    private String buildPrompt(List<ReconTransaction> batch) {
        StringBuilder sb = new StringBuilder();
        sb.append("Layer 1 — Role\n");
        sb.append("You are the Cognologix Finance assistant mapping HDFC bank narrations to TallyPrime ledgers.\n");
        sb.append("Company: ").append(generalConfigService.getConfigValue(CFG_COMPANY).orElse("COGNOLOGIX")).append('\n');
        sb.append("Return only ledgers from the allowed list (exact, case-sensitive names).\n\n");
        sb.append("Layer 2 — Hints\n");
        List<LlmHint> hints = hintRepository.findByActiveTrue();
        if (hints.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (LlmHint hint : hints) {
                sb.append("- [").append(hint.getVoucherType()).append("] ").append(hint.getHintText()).append('\n');
            }
        }
        sb.append("\nLayer 3 — Similar learned mappings\n");
        List<LearnedMapping> learned = learnedMappingRepository.findAll();
        for (ReconTransaction tx : batch) {
            List<LearnedMapping> examples = topFuzzy(tx, learned);
            if (examples.isEmpty() || maxScore(tx, examples) < FUZZY_THRESHOLD) {
                examples = semanticFallback(tx);
            }
            sb.append("Transaction ").append(tx.getId()).append(" examples:\n");
            if (examples.isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (LearnedMapping ex : examples) {
                    sb.append("  - \"").append(ex.getNormalisedNarration()).append("\" -> ")
                            .append(ex.getLedgerName()).append('\n');
                }
            }
        }
        sb.append("\nLayer 4 — Transactions to map\n");
        for (ReconTransaction tx : batch) {
            sb.append("- id=").append(tx.getId())
                    .append(" type=").append(tx.getVoucherType())
                    .append(" amount=").append(tx.getAmount())
                    .append(" narration=\"").append(tx.getDescription()).append("\"")
                    .append(" allowedLedgers=").append(allowedLedgers(tx.getVoucherType())).append('\n');
        }
        return sb.toString();
    }

    private List<LearnedMapping> topFuzzy(ReconTransaction tx, List<LearnedMapping> learned) {
        return learned.stream()
                .filter(m -> m.getVoucherType() == tx.getVoucherType())
                .sorted(Comparator.comparingDouble(
                        (LearnedMapping m) -> FuzzyMappingScorer.score(tx.getNormalisedDescription(),
                                m.getNormalisedNarration())).reversed())
                .limit(5)
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
                    vectorStore.similaritySearch(embedding, tx.getVoucherType().name(), 5);
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
        return ledgerRepository.findByActiveTrueOrderByLedgerNameAsc().stream()
                .filter(l -> switch (type) {
                    case PAYMENT -> "Liability".equals(l.getAccountingNature())
                            || "Expense".equals(l.getAccountingNature());
                    case RECEIPT -> "Asset".equals(l.getAccountingNature())
                            || "Income".equals(l.getAccountingNature());
                    case CONTRA -> l.isBankAccount();
                })
                .map(TallyLedger::getLedgerName)
                .collect(Collectors.toCollection(HashSet::new));
    }

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

    private LedgerResponse toLedger(TallyLedger l) {
        return new LedgerResponse(l.getId(), l.getLedgerName(), l.getGroupName(),
                l.getAccountingNature(), l.isBankAccount(), l.isActive());
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

    public record TagsResponse(List<TagsModel> models) {}

    public record TagsModel(String name) {}
}

package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.DebitCredit;
import com.cognologix.fpa.bankrecon.domain.LearnedMapping;
import com.cognologix.fpa.bankrecon.domain.MappingSource;
import com.cognologix.fpa.bankrecon.domain.ReconTransaction;
import com.cognologix.fpa.bankrecon.domain.TallyLedger;
import com.cognologix.fpa.bankrecon.domain.VoucherType;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.LedgerMappingResult;
import com.cognologix.fpa.bankrecon.repository.LearnedMappingRepository;
import com.cognologix.fpa.bankrecon.repository.LlmHintRepository;
import com.cognologix.fpa.bankrecon.repository.ReconAccountMappingRepository;
import com.cognologix.fpa.bankrecon.repository.ReconTransactionRepository;
import com.cognologix.fpa.bankrecon.repository.TallyLedgerHintRepository;
import com.cognologix.fpa.bankrecon.repository.TallyLedgerRepository;
import com.cognologix.fpa.general.GeneralConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MappingPrecedenceTest {

    private final List<TallyLedger> coded = new ArrayList<>();
    private final List<TallyLedger> catalog = new ArrayList<>();
    private GeneralConfigService config;
    private LearnedMappingRepository learned;
    private StructuredLlmClient llm;
    private BankReconService service;

    @BeforeEach
    void setUp() {
        config = mock(GeneralConfigService.class);
        when(config.getConfigValue(anyString())).thenReturn(Optional.empty());
        learned = mock(LearnedMappingRepository.class);
        when(learned.findByNormalisedNarrationAndVoucherType(anyString(), any())).thenReturn(Optional.empty());
        when(learned.findAll()).thenReturn(List.of());
        when(learned.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TallyLedgerRepository ledgers = mock(TallyLedgerRepository.class);
        when(ledgers.findByLedgerCodeIsNotNull()).thenAnswer(invocation -> List.copyOf(coded));
        when(ledgers.findByActiveTrueOrderByLedgerNameAsc()).thenAnswer(invocation -> List.copyOf(catalog));
        LlmHintRepository hints = mock(LlmHintRepository.class);
        when(hints.findByActiveTrue()).thenReturn(List.of());
        ReconAccountMappingRepository accounts = mock(ReconAccountMappingRepository.class);
        when(accounts.findAll()).thenReturn(List.of());
        TallyLedgerHintRepository ledgerHints = mock(TallyLedgerHintRepository.class);
        when(ledgerHints.findAll()).thenReturn(List.of());
        NarrationEmbedder embedder = mock(NarrationEmbedder.class);
        when(embedder.embed(anyString())).thenReturn(new float[0]);
        llm = mock(StructuredLlmClient.class);
        when(llm.mapBatch(anyString(), anyInt())).thenReturn(LlmBatchResult.failed("down"));
        ReconTransactionRepository transactions = mock(ReconTransactionRepository.class);
        service = new BankReconService(
                null, transactions, null, ledgers, null, learned, hints, null, null, accounts, ledgerHints,
                null, config, null, llm, embedder, null);
    }

    @Test
    void aCodeBeatsALearnedMapping() {
        coded.add(ledger("Sky Payee", "SKY9"));
        String narration = "NEFT SKY9 VENDOR";
        rememberLearned(narration, "Learned Vendor");
        ReconTransaction tx = row(narration);
        tx.setMappingSource(MappingSource.LEARNED);
        tx.setMappedLedger("Learned Vendor");

        service.runMappingPipeline(List.of(tx));

        assertThat(tx.getMappingSource()).isEqualTo(MappingSource.CODE);
        assertThat(tx.getMappedLedger()).isEqualTo("Sky Payee");
        assertThat(tx.getMatchedCode()).isEqualTo("SKY9");
        assertThat(tx.getMappingReason()).isNull();
        verify(learned, never()).findByNormalisedNarrationAndVoucherType(anyString(), any());
        verify(llm, never()).mapBatch(anyString(), anyInt());
    }

    @Test
    void aLearnedMappingIsUsedWhenTheNarrationHasNoCode() {
        String narration = "UPI VENDOR PAYMENT XYZ";
        rememberLearned(narration, "Office Expenses");
        ReconTransaction tx = row(narration);
        tx.setMappingSource(MappingSource.CODE);
        tx.setMatchedCode("SKY9");

        service.runMappingPipeline(List.of(tx));

        assertThat(tx.getMappingSource()).isEqualTo(MappingSource.LEARNED);
        assertThat(tx.getMappedLedger()).isEqualTo("Office Expenses");
        assertThat(tx.getMatchedCode()).isNull();
        verify(llm, never()).mapBatch(anyString(), anyInt());
    }

    @Test
    void neitherCodeNorLearnedCallsTheLlm() {
        catalog.add(ledger("Office Supplies", null));
        when(llm.mapBatch(anyString(), anyInt())).thenReturn(
                LlmBatchResult.complete(List.of(new LedgerMappingResult("1", "Office Supplies"))));
        ReconTransaction tx = row("CLOUD HOSTING AWS");

        service.runMappingPipeline(List.of(tx));

        assertThat(tx.getMappingSource()).isEqualTo(MappingSource.LLM);
        assertThat(tx.getMappedLedger()).isEqualTo("Office Supplies");
        assertThat(tx.getMatchedCode()).isNull();
        assertThat(tx.getMappingReason()).isNull();
        verify(llm).mapBatch(anyString(), anyInt());
    }

    @Test
    void anUnavailableLlmLeavesNoneInsteadOfCode() {
        ReconTransaction tx = row("CLOUD HOSTING AWS");
        tx.setMappingSource(MappingSource.CODE);
        tx.setMatchedCode("SKY9");
        tx.setMappedLedger("Sky Payee");

        service.runMappingPipeline(List.of(tx));

        assertThat(tx.getMappingSource()).isEqualTo(MappingSource.NONE);
        assertThat(tx.getMappingReason()).isEqualTo(RowMapping.LLM_UNAVAILABLE);
        assertThat(tx.getMappedLedger()).isNull();
        assertThat(tx.getMatchedCode()).isNull();
    }

    @Test
    void anOrdinaryWordShowsTheMatchedCodeUntilThatCodeIsIgnored() {
        coded.add(ledger("Atlas Travel", "AT"));
        ReconTransaction tx = row("UPI AT STORE 12");

        service.runMappingPipeline(List.of(tx));
        assertThat(tx.getMappingSource()).isEqualTo(MappingSource.CODE);
        assertThat(tx.getMatchedCode()).isEqualTo("AT");

        when(config.getConfigValue(BankReconService.CFG_CODE_IGNORE)).thenReturn(Optional.of("AT"));
        service.runMappingPipeline(List.of(tx));

        assertThat(tx.getMappingSource()).isEqualTo(MappingSource.NONE);
        assertThat(tx.getMatchedCode()).isNull();
        assertThat(tx.getMappingReason()).isEqualTo(RowMapping.LLM_UNAVAILABLE);
    }

    @Test
    void twoLedgersSharingATokenAfterCaseFoldingFallThrough() {
        coded.add(ledger("Atlas One", "at"));
        coded.add(ledger("Atlas Two", "AT"));
        String narration = "PAY AT NOON";
        rememberLearned(narration, "Learned Co");
        ReconTransaction tx = row(narration);

        service.runMappingPipeline(List.of(tx));

        assertThat(tx.getImportFlag()).isEqualTo("code conflict: AT");
        assertThat(tx.getMappingSource()).isEqualTo(MappingSource.LEARNED);
        assertThat(tx.getMappedLedger()).isEqualTo("Learned Co");
        assertThat(tx.getMatchedCode()).isNull();
        verify(llm, never()).mapBatch(anyString(), anyInt());
    }

    @Test
    void aManualRowIsNotRemapped() {
        coded.add(ledger("Sky Payee", "SKY9"));
        ReconTransaction tx = row("NEFT SKY9 VENDOR");
        tx.setMappingSource(MappingSource.MANUAL);
        tx.setMappedLedger("Hand Ledger");
        tx.setMatchedCode(null);

        service.runMappingPipeline(List.of(tx));

        assertThat(tx.getMappingSource()).isEqualTo(MappingSource.MANUAL);
        assertThat(tx.getMappedLedger()).isEqualTo("Hand Ledger");
        verify(learned, never()).findByNormalisedNarrationAndVoucherType(anyString(), any());
        verify(llm, never()).mapBatch(anyString(), anyInt());
    }

    private void rememberLearned(String narration, String ledgerName) {
        LearnedMapping mapping = LearnedMapping.builder()
                .normalisedNarration(NarrationNormalizer.normalise(narration))
                .voucherType(VoucherType.PAYMENT)
                .ledgerName(ledgerName)
                .useCount(1)
                .lastUsedAt(Instant.now())
                .build();
        when(learned.findByNormalisedNarrationAndVoucherType(
                NarrationNormalizer.normalise(narration), VoucherType.PAYMENT))
                .thenReturn(Optional.of(mapping));
    }

    private static ReconTransaction row(String narration) {
        return ReconTransaction.builder()
                .description(narration)
                .normalisedDescription(NarrationNormalizer.normalise(narration))
                .amount(new BigDecimal("10.00"))
                .debitCredit(DebitCredit.D)
                .voucherType(VoucherType.PAYMENT)
                .build();
    }

    private static TallyLedger ledger(String name, String code) {
        return TallyLedger.builder()
                .id(UUID.randomUUID())
                .ledgerName(name)
                .groupName("Indirect Expenses")
                .accountingNature("Expense")
                .ledgerCode(code)
                .active(true)
                .build();
    }
}

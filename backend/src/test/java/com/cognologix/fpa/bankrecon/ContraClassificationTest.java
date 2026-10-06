package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.ContraRule;
import com.cognologix.fpa.bankrecon.domain.DebitCredit;
import com.cognologix.fpa.bankrecon.domain.PatternType;
import com.cognologix.fpa.bankrecon.domain.ReconTransaction;
import com.cognologix.fpa.bankrecon.domain.VoucherType;
import com.cognologix.fpa.bankrecon.repository.LearnedMappingRepository;
import com.cognologix.fpa.bankrecon.repository.LlmHintRepository;
import com.cognologix.fpa.bankrecon.repository.ReconAccountMappingRepository;
import com.cognologix.fpa.bankrecon.repository.TallyLedgerHintRepository;
import com.cognologix.fpa.bankrecon.repository.TallyLedgerRepository;
import com.cognologix.fpa.general.GeneralConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HDFC internal transfers whose company token sits after a hyphen.
 * Upload stores {@link NarrationNormalizer#normalise(String)} and then classifies.
 */
class ContraClassificationTest {

    private static final String RTGS =
            "RTGS Cr-DEUT0784PBC-COGNOLOGIX TECHNOLOGIES PVT LTD-COGNOLOGIX TECHNOLOGIES PRIVATE LIM-DEUTR52026082898560136";
    private static final String NEFT =
            "NEFT Cr-DEUT0784PBC-COGNOLOGIX TECHNOLOGIES PVT LTD-COGNOLOGIX TECHNOLOGIES PRIVATE LIM-DEUTN26243583730";

    private GeneralConfigService config;
    private StructuredLlmClient llm;
    private BankReconService service;

    @BeforeEach
    void setUp() {
        config = mock(GeneralConfigService.class);
        when(config.getConfigValue(anyString())).thenReturn(Optional.empty());
        when(config.getConfigValue(BankReconService.CFG_COMPANY)).thenReturn(Optional.of("COGNOLOGIX"));

        LlmHintRepository hints = mock(LlmHintRepository.class);
        when(hints.findByActiveTrue()).thenReturn(List.of());
        TallyLedgerRepository ledgers = mock(TallyLedgerRepository.class);
        when(ledgers.findByActiveTrueOrderByLedgerNameAsc()).thenReturn(List.of());
        LearnedMappingRepository learned = mock(LearnedMappingRepository.class);
        when(learned.findAll()).thenReturn(List.of());
        ReconAccountMappingRepository accounts = mock(ReconAccountMappingRepository.class);
        when(accounts.findAll()).thenReturn(List.of());
        TallyLedgerHintRepository ledgerHints = mock(TallyLedgerHintRepository.class);
        when(ledgerHints.findAll()).thenReturn(List.of());
        NarrationEmbedder embedder = mock(NarrationEmbedder.class);
        when(embedder.embed(anyString())).thenReturn(new float[0]);
        llm = mock(StructuredLlmClient.class);
        when(llm.mapBatch(anyString(), anyInt())).thenReturn(LlmBatchResult.complete(List.of()));

        service = new BankReconService(
                null, null, null, ledgers, null, learned, hints, null, null, accounts, ledgerHints,
                null, config, null, llm, embedder);
    }

    @Test
    void seededPrefixRulesClassifyHdfcInternalTransfersAsContra() {
        List<ContraRule> rules = seededRules();
        ReconTransaction rtgs = asUploaded(RTGS, "2300000.00");
        ReconTransaction neft = asUploaded(NEFT, "4900000.00");

        assertThat(NarrationNormalizer.normalise(RTGS))
                .contains("COGNOLOGIX")
                .doesNotContain("DEUT0784PBC")
                .startsWith("RTGS");
        assertThat(service.classifyTransaction(rtgs, rules)).isEqualTo(VoucherType.CONTRA);
        assertThat(service.classifyTransaction(neft, rules)).isEqualTo(VoucherType.CONTRA);

        when(config.getConfigValue(BankReconService.CFG_COMPANY)).thenReturn(Optional.of("  cognologix  "));
        assertThat(service.classifyTransaction(rtgs, rules)).isEqualTo(VoucherType.CONTRA);
    }

    @Test
    void contraRowsAreExcludedFromLlmBatchesAndStayUnmapped() {
        List<ContraRule> rules = seededRules();
        ReconTransaction rtgs = asUploaded(RTGS, "2300000.00");
        ReconTransaction neft = asUploaded(NEFT, "4900000.00");
        rtgs.setVoucherType(service.classifyTransaction(rtgs, rules));
        neft.setVoucherType(service.classifyTransaction(neft, rules));

        ReconTransaction rent = asUploaded("OFFICE RENT AUG", "40000.00");
        rent.setDebitCredit(DebitCredit.D);
        rent.setVoucherType(service.classifyTransaction(rent, rules));

        service.mapBatchWithLlm(List.of(rtgs, neft, rent));

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(llm).mapBatch(prompts.capture(), anyInt());
        assertThat(prompts.getAllValues())
                .isNotEmpty()
                .allSatisfy(prompt -> {
                    assertThat(prompt).doesNotContain("COGNOLOGIX TECHNOLOGIES PVT LTD");
                    assertThat(prompt).doesNotContain(RTGS);
                    assertThat(prompt).doesNotContain(NEFT);
                    assertThat(prompt).contains("OFFICE RENT AUG");
                });
        assertThat(rtgs.getVoucherType()).isEqualTo(VoucherType.CONTRA);
        assertThat(neft.getVoucherType()).isEqualTo(VoucherType.CONTRA);
        assertThat(rtgs.getMappedLedger()).isNull();
        assertThat(neft.getMappedLedger()).isNull();
        assertThat(rtgs.getMappingSource()).isNull();
        assertThat(neft.getMappingSource()).isNull();
    }

    @Test
    void contraOnlyBatchNeverCallsTheLlm() {
        List<ContraRule> rules = seededRules();
        ReconTransaction rtgs = asUploaded(RTGS, "2300000.00");
        ReconTransaction neft = asUploaded(NEFT, "4900000.00");
        rtgs.setVoucherType(service.classifyTransaction(rtgs, rules));
        neft.setVoucherType(service.classifyTransaction(neft, rules));

        service.mapBatchWithLlm(List.of(rtgs, neft));

        verify(llm, never()).mapBatch(anyString(), anyInt());
        assertThat(rtgs.getMappedLedger()).isNull();
        assertThat(neft.getMappedLedger()).isNull();
    }

    private static List<ContraRule> seededRules() {
        return List.of(
                ContraRule.builder().patternType(PatternType.PREFIX).patternValue("NEFT").active(true).build(),
                ContraRule.builder().patternType(PatternType.PREFIX).patternValue("RTGS").active(true).build());
    }

    private static ReconTransaction asUploaded(String narration, String amount) {
        return ReconTransaction.builder()
                .description(narration)
                .normalisedDescription(NarrationNormalizer.normalise(narration))
                .amount(new BigDecimal(amount))
                .debitCredit(DebitCredit.C)
                .build();
    }
}

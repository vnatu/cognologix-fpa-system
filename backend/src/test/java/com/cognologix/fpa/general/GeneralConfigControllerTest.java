package com.cognologix.fpa.general;

import com.cognologix.fpa.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {GeneralConfigController.class, GeneralExceptionHandler.class})
@Import(TestSecurityConfig.class)
class GeneralConfigControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean GeneralConfigService generalConfigService;

    private FxRate sampleFxRate() {
        return FxRate.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .currencyPair("USD_INR")
                .rate(new BigDecimal("84.5000"))
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .createdAt(Instant.now())
                .build();
    }

    private ConcentrationRiskConfig sampleConfig() {
        var c = new ConcentrationRiskConfig();
        c.setId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        c.setSingleClientThresholdPct(new BigDecimal("30.00"));
        return c;
    }

    @Test
    void listFxRates_returnsOk() throws Exception {
        when(generalConfigService.findAllFxRates()).thenReturn(List.of(sampleFxRate()));
        mockMvc.perform(get("/api/general/fx-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currencyPair").value("USD_INR"))
                .andExpect(jsonPath("$[0].rate").value(84.5));
    }

    @Test
    void createFxRate_returnsCreated() throws Exception {
        when(generalConfigService.findActiveRate("USD_INR")).thenReturn(Optional.empty());
        when(generalConfigService.createFxRate(any(), any(), any(), any())).thenReturn(sampleFxRate());
        var body = """
                {"currencyPair":"USD_INR","rate":84.50,"effectiveFrom":"2026-01-01"}
                """;
        mockMvc.perform(post("/api/general/fx-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currencyPair").value("USD_INR"));
    }

    @Test
    void createFxRate_autoClosesExistingRate() throws Exception {
        var existing = sampleFxRate();
        when(generalConfigService.findActiveRate("USD_INR")).thenReturn(Optional.of(existing));
        when(generalConfigService.closeFxRate(any(), any())).thenReturn(existing);
        when(generalConfigService.createFxRate(any(), any(), any(), any())).thenReturn(sampleFxRate());
        var body = """
                {"currencyPair":"USD_INR","rate":85.00,"effectiveFrom":"2026-04-01"}
                """;
        mockMvc.perform(post("/api/general/fx-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void getConcentrationRiskConfig_returnsOk() throws Exception {
        when(generalConfigService.getConcentrationRiskConfig()).thenReturn(sampleConfig());
        mockMvc.perform(get("/api/general/concentration-risk-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.singleClientThresholdPct").value(30.00));
    }

    @Test
    void updateConcentrationRiskConfig_returnsOk() throws Exception {
        when(generalConfigService.updateSingleClientThreshold(any())).thenReturn(sampleConfig());
        var body = """
                {"singleClientThresholdPct":25.00}
                """;
        mockMvc.perform(put("/api/general/concentration-risk-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.singleClientThresholdPct").value(30.00));
    }

    @Test
    void createFxRate_invalidRate_returns400() throws Exception {
        var body = """
                {"currencyPair":"USD_INR","rate":0,"effectiveFrom":"2026-01-01"}
                """;
        mockMvc.perform(post("/api/general/fx-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDateFormat_returnsOk() throws Exception {
        when(generalConfigService.getDateFormat()).thenReturn("DD MMM YYYY");
        mockMvc.perform(get("/api/general/config/date-format"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("DD MMM YYYY"));
    }

    @Test
    void updateDateFormat_returnsOk() throws Exception {
        when(generalConfigService.updateDateFormat("DD/MM/YYYY")).thenReturn("DD/MM/YYYY");
        var body = """
                {"format":"DD/MM/YYYY"}
                """;
        mockMvc.perform(put("/api/general/config/date-format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("DD/MM/YYYY"));
    }

    @Test
    void updateDateFormat_invalidFormat_returns400() throws Exception {
        var body = """
                {"format":"YYYY-MM-DD"}
                """;
        mockMvc.perform(put("/api/general/config/date-format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}

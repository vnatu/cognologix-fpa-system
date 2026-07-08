package com.cognologix.fpa.customer;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@Import(TestSecurityConfig.class)
class CustomerControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CustomerService customerService;

    private Customer sampleCustomer() {
        var c = new Customer();
        c.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        c.setCustomerCode("ICERTI");
        c.setCustomerName("Icertis");
        c.setLifecycleStatus(LifecycleStatus.ACTIVE);
        c.setProjectCodes(List.of());
        return c;
    }

    @Test
    void listCustomers_returnsOk() throws Exception {
        when(customerService.findAllCustomers()).thenReturn(List.of(sampleCustomer()));
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerCode").value("ICERTI"));
    }

    @Test
    void createCustomer_returnsCreated() throws Exception {
        when(customerService.createCustomer(any(), any(), any(), any(), any(), any()))
                .thenReturn(sampleCustomer());
        var body = """
                {"customerCode":"ICERTI","customerName":"Icertis","lifecycleStatus":"ACTIVE"}
                """;
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerCode").value("ICERTI"));
    }

    @Test
    void getCustomer_found_returnsOk() throws Exception {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(customerService.findById(id)).thenReturn(Optional.of(sampleCustomer()));
        mockMvc.perform(get("/api/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Icertis"));
    }

    @Test
    void getCustomer_notFound_returns404() throws Exception {
        when(customerService.findById(any())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/customers/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCustomer_returnsOk() throws Exception {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(customerService.updateCustomer(eq(id), any(), any(), any(), any()))
                .thenReturn(sampleCustomer());
        var body = """
                {"lifecycleStatus":"AT_RISK"}
                """;
        mockMvc.perform(put("/api/customers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void listRateCards_returnsOk() throws Exception {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var card = new RateCard();
        card.setId(UUID.randomUUID());
        card.setName("FY2526 Standard");
        card.setRateCardType(RateCardType.FLAT);
        card.setCurrency(RateCurrency.INR);
        card.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        card.setLines(List.of());
        when(customerService.findRateCardsByCustomer(id)).thenReturn(List.of(card));
        mockMvc.perform(get("/api/customers/{id}/rate-cards", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rateCardType").value("FLAT"))
                .andExpect(jsonPath("$[0].currency").value("INR"))
                .andExpect(jsonPath("$[0].name").value("FY2526 Standard"));
    }

    @Test
    void createRateCard_returnsCreated() throws Exception {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var card = new RateCard();
        card.setId(UUID.randomUUID());
        card.setName("FY2526 Standard");
        card.setRateCardType(RateCardType.FLAT);
        card.setCurrency(RateCurrency.INR);
        card.setEffectiveFrom(LocalDate.of(2026, 4, 1));
        card.setLines(List.of());
        when(customerService.createRateCard(eq(id), any(), any(), any(), any(), any())).thenReturn(card);
        var body = """
                {"name":"FY2526 Standard","rateCardType":"FLAT","currency":"INR",
                 "effectiveFrom":"2026-04-01","lines":[{"rateAmount":150000}]}
                """;
        mockMvc.perform(post("/api/customers/{id}/rate-cards", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("FY2526 Standard"))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void listProjectCodes_returnsOk() throws Exception {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(customerService.findProjectCodesByCustomer(id)).thenReturn(List.of());
        mockMvc.perform(get("/api/customers/{id}/project-codes", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void addProjectCode_returnsCreated() throws Exception {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var pc = new CustomerProjectCode();
        pc.setId(UUID.randomUUID());
        pc.setProjectCode("PROJ001");
        when(customerService.addProjectCode(eq(id), any(), any())).thenReturn(pc);
        var body = """
                {"projectCode":"PROJ001","description":"Main project"}
                """;
        mockMvc.perform(post("/api/customers/{id}/project-codes", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectCode").value("PROJ001"));
    }

    @Test
    void removeProjectCode_returnsNoContent() throws Exception {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var codeId = UUID.randomUUID();
        doNothing().when(customerService).removeProjectCode(id, codeId);
        mockMvc.perform(delete("/api/customers/{id}/project-codes/{codeId}", id, codeId))
                .andExpect(status().isNoContent());
    }

    @Test
    void createCustomer_missingRequiredField_returns400() throws Exception {
        var body = """
                {"customerName":"Icertis"}
                """;
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}

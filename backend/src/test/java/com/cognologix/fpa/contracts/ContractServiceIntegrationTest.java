package com.cognologix.fpa.contracts;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.contracts.repository.ContractTypeRepository;
import com.cognologix.fpa.general.UserRole;
import com.cognologix.fpa.general.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Testcontainers
class ContractServiceIntegrationTest {

    private static final String ACTOR = "test-admin@cognologix.com";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired MockMvc mockMvc;
    @Autowired ContractService contractService;
    @Autowired ContractTypeRepository contractTypeRepository;
    @Autowired UserService userService;
    @Autowired JdbcTemplate jdbcTemplate;

    private UUID ndaTypeId;

    @BeforeEach
    void setUp() {
        if (userService.findByEmail(ACTOR).isEmpty()) {
            userService.createUser(ACTOR, "Test Admin", UserRole.ADMIN, "TestPassword1!", "test");
        }
        jdbcTemplate.execute("DELETE FROM contract_notification_log");
        jdbcTemplate.execute("DELETE FROM contract_document");
        jdbcTemplate.execute("DELETE FROM contract_version");
        jdbcTemplate.execute("UPDATE contract SET parent_contract_id = NULL");
        jdbcTemplate.execute("DELETE FROM contract");
        jdbcTemplate.execute("DELETE FROM contract_template_document");
        jdbcTemplate.execute("DELETE FROM contract_template");
        jdbcTemplate.execute("DELETE FROM app_notification");
        ndaTypeId = contractTypeRepository.findByTypeCodeIgnoreCase("NDA").orElseThrow().getId();
    }

    @Test
    void createContract_generatesNumber_andRejectsBothPartyFields() throws Exception {
        mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractJson("Icertis NDA", "Icertis", LocalDate.now().plusDays(30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contractNumber").value(startsWith("CON-" + LocalDate.now().getYear() + "-")))
                .andExpect(jsonPath("$.partyDisplayName").value("Icertis"))
                .andExpect(jsonPath("$.ownerEmail").value(ACTOR));

        mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Both",
                                  "contractTypeId": "%s",
                                  "paperType": "OWN",
                                  "customerId": "%s",
                                  "partyName": "Someone",
                                  "status": "ACTIVE"
                                }
                                """.formatted(ndaTypeId, UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addVersion_supersedesPrevious_andEnforcesSignedRules() throws Exception {
        String contractId = createContract("Versioned NDA", "Acme", LocalDate.now().plusDays(90));

        MvcResult first = uploadVersion(contractId, "v1", null);
        String firstVersionId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.id");
        String documentId = com.jayway.jsonpath.JsonPath.read(
                first.getResponse().getContentAsString(), "$.documents[0].id");

        uploadVersion(contractId, "Final", "SIGNED");

        mockMvc.perform(get("/api/contracts/" + contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0].versionLabel").value("Final"))
                .andExpect(jsonPath("$.versions[0].status").value("SIGNED"))
                .andExpect(jsonPath("$.versions[1].status").value("SUPERSEDED"));

        mockMvc.perform(put("/api/contracts/" + contractId + "/versions/" + firstVersionId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SIGNED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/contracts/" + contractId + "/versions/" + firstVersionId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUPERSEDED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/contracts/" + contractId + "/versions/" + firstVersionId
                        + "/documents/" + documentId + "/download"))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsByteArray()).containsExactly("signed-body".getBytes()));
    }

    @Test
    void expiryNotification_isSentOnce() throws Exception {
        String contractId = createContract("Expiring NDA", "Northwind", LocalDate.now(ContractService.IST).plusDays(30));

        contractService.runExpiryNotifications();
        contractService.runExpiryNotifications();

        mockMvc.perform(get("/api/contracts/" + contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationHistory", hasSize(1)))
                .andExpect(jsonPath("$.notificationHistory[0].notificationType").value("IN_APP"))
                .andExpect(jsonPath("$.notificationHistory[0].daysBeforeExpiry").value(30));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].link").value("/contracts/" + contractId));

        mockMvc.perform(get("/api/contracts/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiringIn30Days[0].id").value(contractId));
    }

    private String createContract(String title, String party, LocalDate expiry) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractJson(title, party, expiry)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MvcResult uploadVersion(String contractId, String label, String status) throws Exception {
        String json = status == null
                ? "{\"versionLabel\":\"" + label + "\"}"
                : "{\"versionLabel\":\"" + label + "\",\"status\":\"" + status + "\"}";
        var metadata = new MockMultipartFile(
                "metadata", "metadata.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
        var primary = new MockMultipartFile(
                "primaryFile", "nda.pdf", "application/pdf", "signed-body".getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/contracts/" + contractId + "/versions")
                        .file(metadata)
                        .file(primary))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String contractJson(String title, String party, LocalDate expiry) {
        return """
                {
                  "title": "%s",
                  "contractTypeId": "%s",
                  "paperType": "THIRD_PARTY",
                  "partyName": "%s",
                  "status": "ACTIVE",
                  "evergreen": false,
                  "expiryDate": "%s"
                }
                """.formatted(title, ndaTypeId, party, expiry);
    }
}

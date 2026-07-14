package com.cognologix.fpa.people;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.people.domain.ExitStatus;
import com.cognologix.fpa.people.domain.MasterRecord;
import com.cognologix.fpa.people.domain.ReconciliationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MasterDataController.class)
@Import(TestSecurityConfig.class)
class MasterDataControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PeoplePayrollService peoplePayrollService;

    @Test
    void listMaster_returnsOk() throws Exception {
        var versionId = UUID.randomUUID();
        var registry = EmployeeRegistry.builder()
                .id(UUID.randomUUID())
                .employeeId("EMP001")
                .fullName("Ada")
                .exitStatus(ExitStatus.ACTIVE)
                .build();
        var master = MasterRecord.builder()
                .id(UUID.randomUUID())
                .employeeRegistry(registry)
                .practiceUnit("Product Engineering")
                .businessUnit("Icertis")
                .billableStatus("Y")
                .grossPay(new BigDecimal("100000.00"))
                .deliveryPu(true)
                .billable(true)
                .reconciliationStatus(ReconciliationStatus.MATCHED)
                .build();
        when(peoplePayrollService.findMasterRecords(versionId)).thenReturn(List.of(master));

        mockMvc.perform(get("/api/people/master/{id}", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value("EMP001"))
                .andExpect(jsonPath("$[0].reconciliationStatus").value("MATCHED"));
    }

    @Test
    void summary_returnsOk() throws Exception {
        var versionId = UUID.randomUUID();
        when(peoplePayrollService.summarizeMaster(versionId))
                .thenReturn(new PeoplePayrollService.MasterSummary(
                        1, new BigDecimal("100000"),
                        0, BigDecimal.ZERO,
                        0, BigDecimal.ZERO,
                        0, BigDecimal.ZERO,
                        0, BigDecimal.ZERO,
                        List.of(new PeoplePayrollService.BuBreakdown("Icertis", 1, new BigDecimal("100000")))));

        mockMvc.perform(get("/api/people/master/{id}/summary", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billable.headcount").value(1))
                .andExpect(jsonPath("$.byBusinessUnit[0].businessUnit").value("Icertis"));
    }

    @Test
    void reconcile_returnsOk() throws Exception {
        var versionId = UUID.randomUUID();
        var registry = EmployeeRegistry.builder()
                .id(UUID.randomUUID())
                .employeeId("EMP001")
                .fullName("Ada")
                .exitStatus(ExitStatus.ACTIVE)
                .build();
        var master = MasterRecord.builder()
                .id(UUID.randomUUID())
                .employeeRegistry(registry)
                .grossPay(new BigDecimal("50000"))
                .reconciliationStatus(ReconciliationStatus.MANUALLY_MAPPED)
                .build();
        when(peoplePayrollService.reconcileManually(eq(versionId), any(), any(), anyString()))
                .thenReturn(master);

        var body = "{\"payrollSnapshotId\":\"" + UUID.randomUUID()
                + "\",\"employeeRegistryId\":\"" + registry.getId() + "\"}";
        mockMvc.perform(post("/api/people/master/{id}/reconcile", versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliationStatus").value("MANUALLY_MAPPED"));
    }
}

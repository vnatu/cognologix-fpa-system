package com.cognologix.fpa.people;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.people.domain.Period;
import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.domain.PeriodVersion;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@WebMvcTest(PeriodController.class)
@Import(TestSecurityConfig.class)
class PeriodControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean PeoplePayrollService peoplePayrollService;

    @Test
    void createPeriod_returnsCreated() throws Exception {
        var periodId = UUID.randomUUID();
        var period = Period.builder().id(periodId).periodMonth(3).periodYear(2026).build();
        var version = PeriodVersion.builder()
                .id(UUID.randomUUID())
                .period(period)
                .versionNumber(1)
                .status(PeriodStatus.OPEN)
                .latestFinalised(false)
                .build();
        when(peoplePayrollService.createPeriod(3, 2026)).thenReturn(period);
        when(peoplePayrollService.findVersionsForPeriod(periodId)).thenReturn(List.of(version));

        mockMvc.perform(post("/api/people/periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodMonth\":3,\"periodYear\":2026}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodMonth").value(3))
                .andExpect(jsonPath("$.versions[0].versionNumber").value(1))
                .andExpect(jsonPath("$.versions[0].status").value("OPEN"));
    }

    @Test
    void listPeriods_returnsOk() throws Exception {
        when(peoplePayrollService.findAllPeriods()).thenReturn(List.of());
        mockMvc.perform(get("/api/people/periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getVersion_returnsDetail() throws Exception {
        var periodId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var period = Period.builder().id(periodId).periodMonth(5).periodYear(2026).build();
        var version = PeriodVersion.builder()
                .id(versionId)
                .period(period)
                .versionNumber(1)
                .status(PeriodStatus.OPEN)
                .build();
        when(peoplePayrollService.getPeriodVersion(periodId, versionId)).thenReturn(version);
        when(peoplePayrollService.findUploadsForVersion(versionId)).thenReturn(List.of());
        when(peoplePayrollService.countMasterByReconciliationStatus(versionId)).thenReturn(Map.of());

        mockMvc.perform(get("/api/people/periods/{periodId}/versions/{versionId}", periodId, versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1));
    }

    @Test
    void buildMaster_returnsRecords() throws Exception {
        var periodId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var period = Period.builder().id(periodId).periodMonth(5).periodYear(2026).build();
        var version = PeriodVersion.builder().id(versionId).period(period).versionNumber(1)
                .status(PeriodStatus.SNAPSHOTS_UPLOADED).build();
        when(peoplePayrollService.getPeriodVersion(periodId, versionId)).thenReturn(version);
        when(peoplePayrollService.buildMasterRecords(versionId)).thenReturn(List.of());

        mockMvc.perform(post("/api/people/periods/{p}/versions/{v}/build-master", periodId, versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void finalise_returnsNoContent() throws Exception {
        var periodId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var period = Period.builder().id(periodId).periodMonth(5).periodYear(2026).build();
        var version = PeriodVersion.builder().id(versionId).period(period).versionNumber(1)
                .status(PeriodStatus.MASTER_BUILT).build();
        when(peoplePayrollService.getPeriodVersion(periodId, versionId)).thenReturn(version);
        doNothing().when(peoplePayrollService).finalisePeriod(eq(versionId), any());

        mockMvc.perform(post("/api/people/periods/{p}/versions/{v}/finalise", periodId, versionId))
                .andExpect(status().isNoContent());
    }
}

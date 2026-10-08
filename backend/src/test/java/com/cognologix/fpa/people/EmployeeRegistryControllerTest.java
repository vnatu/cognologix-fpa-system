package com.cognologix.fpa.people;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.people.domain.ExitStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@WebMvcTest(EmployeeRegistryController.class)
@Import(TestSecurityConfig.class)
class EmployeeRegistryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PeoplePayrollService peoplePayrollService;

    @Test
    void listRegistry_returnsOk() throws Exception {
        var emp = EmployeeRegistry.builder()
                .id(UUID.randomUUID())
                .employeeId("EMP001")
                .fullName("Ada Lovelace")
                .exitStatus(ExitStatus.ACTIVE)
                .build();
        when(peoplePayrollService.findAllEmployees()).thenReturn(List.of(emp));

        mockMvc.perform(get("/api/people/registry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value("EMP001"))
                .andExpect(jsonPath("$[0].fullName").value("Ada Lovelace"));
    }

    @Test
    void getByEmployeeId_found() throws Exception {
        var emp = EmployeeRegistry.builder()
                .id(UUID.randomUUID())
                .employeeId("EMP001")
                .fullName("Ada Lovelace")
                .exitStatus(ExitStatus.ACTIVE)
                .build();
        when(peoplePayrollService.findEmployeeByEmployeeId("EMP001")).thenReturn(Optional.of(emp));

        mockMvc.perform(get("/api/people/registry/{id}", "EMP001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("EMP001"));
    }

    @Test
    void listAlternateIds_returnsOk() throws Exception {
        when(peoplePayrollService.findAllAlternateIdLinks()).thenReturn(List.of());
        mockMvc.perform(get("/api/people/registry/alternate-ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}

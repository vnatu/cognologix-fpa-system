package com.cognologix.fpa.people;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.people.domain.ClassificationConfig;
import com.cognologix.fpa.people.domain.ClassificationConfigType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClassificationConfigController.class)
@Import(TestSecurityConfig.class)
class ClassificationConfigControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PeoplePayrollService peoplePayrollService;

    @Test
    void listClassification_returnsGrouped() throws Exception {
        when(peoplePayrollService.findAllClassificationConfig()).thenReturn(List.of(
                ClassificationConfig.builder()
                        .id(UUID.randomUUID())
                        .configType(ClassificationConfigType.DELIVERY_PU)
                        .value("Product Engineering")
                        .build()));

        mockMvc.perform(get("/api/people/config/classification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.DELIVERY_PU[0].value").value("Product Engineering"));
    }

    @Test
    void addClassification_returnsCreated() throws Exception {
        when(peoplePayrollService.addClassificationConfig(
                eq(ClassificationConfigType.DELIVERY_PU), eq("Platform")))
                .thenReturn(ClassificationConfig.builder()
                        .id(UUID.randomUUID())
                        .configType(ClassificationConfigType.DELIVERY_PU)
                        .value("Platform")
                        .build());

        mockMvc.perform(post("/api/people/config/classification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configType\":\"DELIVERY_PU\",\"value\":\"Platform\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value("Platform"));
    }

    @Test
    void deleteClassification_returnsNoContent() throws Exception {
        var id = UUID.randomUUID();
        doNothing().when(peoplePayrollService).deleteClassificationConfig(id);
        mockMvc.perform(delete("/api/people/config/classification/{id}", id))
                .andExpect(status().isNoContent());
    }
}

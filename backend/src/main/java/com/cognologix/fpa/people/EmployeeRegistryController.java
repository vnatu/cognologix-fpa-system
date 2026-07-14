package com.cognologix.fpa.people;

import com.cognologix.fpa.people.dto.AlternateIdLinkResponse;
import com.cognologix.fpa.people.dto.EmployeeRegistryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/people/registry")
@RequiredArgsConstructor
@Tag(name = "People — Employee Registry", description = "Persistent employee identity and alternate-ID links")
public class EmployeeRegistryController {

    private final PeoplePayrollService peoplePayrollService;

    @GetMapping
    @Operation(summary = "List all employees in the registry")
    public List<EmployeeRegistryResponse> list() {
        return peoplePayrollService.findAllEmployees().stream()
                .map(EmployeeRegistryResponse::from)
                .toList();
    }

    @GetMapping("/alternate-ids")
    @Operation(summary = "List all alternate ID links (reconciliation audit)")
    public List<AlternateIdLinkResponse> listAlternateIds() {
        return peoplePayrollService.findAllAlternateIdLinks().stream()
                .map(AlternateIdLinkResponse::from)
                .toList();
    }

    @GetMapping("/{employeeId}")
    @Operation(summary = "Get a single registry entry by employee_id")
    public ResponseEntity<EmployeeRegistryResponse> get(@PathVariable String employeeId) {
        return peoplePayrollService.findEmployeeByEmployeeId(employeeId)
                .map(EmployeeRegistryResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

package com.cognologix.fpa.customer;

import com.cognologix.fpa.customer.domain.RateCardLine;
import com.cognologix.fpa.customer.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customer Management", description = "Customer Master, rate cards, and project codes")
public class CustomerController {

    private final CustomerService customerService;

    // ── Customer Master ──────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all customers")
    public List<CustomerSummaryResponse> listCustomers() {
        return customerService.findAllCustomers().stream()
                .map(CustomerSummaryResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Create a new customer")
    public ResponseEntity<CustomerSummaryResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest req) {
        var customer = customerService.createCustomer(
                req.customerCode(), req.customerName(), req.zohoBooksCustomerRef(),
                req.relationshipOwnerEmployeeId(), req.lifecycleStatus(), req.dsoDays());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CustomerSummaryResponse.from(customer));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get customer detail")
    public ResponseEntity<CustomerDetailResponse> getCustomer(@PathVariable UUID id) {
        return customerService.findById(id)
                .map(CustomerDetailResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer — name, lifecycle status, relationship owner, DSO. " +
            "No DELETE: use lifecycleStatus=CHURNED to retire a customer (historical data preserved).")
    public ResponseEntity<CustomerDetailResponse> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest req) {
        var updated = customerService.updateCustomer(
                id, req.customerName(), req.lifecycleStatus(),
                req.relationshipOwnerEmployeeId(), req.dsoDays());
        return ResponseEntity.ok(CustomerDetailResponse.from(updated));
    }

    // ── Rate Cards ───────────────────────────────────────────────────────────

    @GetMapping("/{id}/rate-cards")
    @Operation(summary = "List all rate cards for a customer (newest first)")
    public ResponseEntity<List<RateCardResponse>> listRateCards(@PathVariable UUID id) {
        var cards = customerService.findRateCardsByCustomer(id).stream()
                .map(RateCardResponse::from)
                .toList();
        return ResponseEntity.ok(cards);
    }

    @PostMapping("/{id}/rate-cards")
    @Operation(summary = "Create a new rate card with name, type, currency, and effective date. " +
            "Currency applies to all lines in the card (ADR-020). " +
            "Automatically closes the current active rate card. " +
            "No PUT — effective-dated model: supersede by creating a new card (spec §6).")
    public ResponseEntity<RateCardResponse> createRateCard(
            @PathVariable UUID id,
            @Valid @RequestBody CreateRateCardRequest req) {
        var lines = req.lines().stream()
                .map(l -> RateCardLine.builder()
                        .jobLevel(l.jobLevel())
                        .rateAmount(l.rateAmount())
                        .build())
                .toList();
        var card = customerService.createRateCard(
                id, req.name(), req.rateCardType(), req.currency(), req.effectiveFrom(), lines);
        return ResponseEntity.status(HttpStatus.CREATED).body(RateCardResponse.from(card));
    }

    // ── Project Codes ────────────────────────────────────────────────────────

    @GetMapping("/{id}/project-codes")
    @Operation(summary = "List project codes for a customer")
    public ResponseEntity<List<ProjectCodeResponse>> listProjectCodes(@PathVariable UUID id) {
        var codes = customerService.findProjectCodesByCustomer(id).stream()
                .map(ProjectCodeResponse::from)
                .toList();
        return ResponseEntity.ok(codes);
    }

    @PostMapping("/{id}/project-codes")
    @Operation(summary = "Add a project code to a customer")
    public ResponseEntity<ProjectCodeResponse> addProjectCode(
            @PathVariable UUID id,
            @Valid @RequestBody AddProjectCodeRequest req) {
        var pc = customerService.addProjectCode(id, req.projectCode(), req.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectCodeResponse.from(pc));
    }

    @DeleteMapping("/{id}/project-codes/{codeId}")
    @Operation(summary = "Remove a project code from a customer")
    public ResponseEntity<Void> removeProjectCode(
            @PathVariable UUID id,
            @PathVariable UUID codeId) {
        customerService.removeProjectCode(id, codeId);
        return ResponseEntity.noContent().build();
    }
}

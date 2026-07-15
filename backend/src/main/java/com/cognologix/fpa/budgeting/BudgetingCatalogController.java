package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.dto.BudgetingResponses.OverheadLineItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/budgeting")
@RequiredArgsConstructor
@Tag(name = "Budgeting & Forecasting", description = "Catalog endpoints for budgeting reference data")
public class BudgetingCatalogController {

    private final BudgetingService budgetingService;

    @GetMapping("/overhead-line-items")
    @Operation(summary = "List all overhead line items sorted by sortOrder")
    public List<OverheadLineItemResponse> listOverheadLineItems() {
        return budgetingService.listOverheadLineItems().stream()
                .map(OverheadLineItemResponse::from)
                .toList();
    }
}

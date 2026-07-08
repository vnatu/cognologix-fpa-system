package com.cognologix.fpa.customer.dto;

import com.cognologix.fpa.customer.domain.LifecycleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 50) String customerCode,
        @NotBlank @Size(max = 255) String customerName,
        @Size(max = 100) String zohoBooksCustomerRef,
        @Size(max = 50) String relationshipOwnerEmployeeId,
        @NotNull LifecycleStatus lifecycleStatus,
        @Min(0) Integer dsoDays
) {}

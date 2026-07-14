package com.cognologix.fpa.customer.dto;

import com.cognologix.fpa.customer.domain.LifecycleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
        @Size(max = 50) String customerCode,
        @Size(max = 255) String customerName,
        LifecycleStatus lifecycleStatus,
        @Size(max = 50) String relationshipOwnerEmployeeId,
        @Min(0) Integer dsoDays
) {}

package com.cognologix.fpa.customer.dto;

import com.cognologix.fpa.customer.domain.Customer;
import com.cognologix.fpa.customer.domain.LifecycleStatus;

import java.util.UUID;

public record CustomerSummaryResponse(
        UUID id,
        String customerCode,
        String customerName,
        String zohoBooksCustomerRef,
        String relationshipOwnerEmployeeId,
        LifecycleStatus lifecycleStatus
) {
    public static CustomerSummaryResponse from(Customer c) {
        return new CustomerSummaryResponse(
                c.getId(), c.getCustomerCode(), c.getCustomerName(),
                c.getZohoBooksCustomerRef(), c.getRelationshipOwnerEmployeeId(),
                c.getLifecycleStatus());
    }
}

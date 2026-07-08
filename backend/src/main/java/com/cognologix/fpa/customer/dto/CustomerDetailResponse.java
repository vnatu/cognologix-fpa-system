package com.cognologix.fpa.customer.dto;

import com.cognologix.fpa.customer.domain.Customer;
import com.cognologix.fpa.customer.domain.LifecycleStatus;

import java.util.List;
import java.util.UUID;

public record CustomerDetailResponse(
        UUID id,
        String customerCode,
        String customerName,
        String zohoBooksCustomerRef,
        String relationshipOwnerEmployeeId,
        LifecycleStatus lifecycleStatus,
        CommercialTermsResponse commercialTerms,
        List<ProjectCodeResponse> projectCodes
) {
    public static CustomerDetailResponse from(Customer c) {
        return new CustomerDetailResponse(
                c.getId(), c.getCustomerCode(), c.getCustomerName(),
                c.getZohoBooksCustomerRef(), c.getRelationshipOwnerEmployeeId(),
                c.getLifecycleStatus(),
                c.getCommercialTerms() != null ? CommercialTermsResponse.from(c.getCommercialTerms()) : null,
                c.getProjectCodes().stream().map(ProjectCodeResponse::from).toList());
    }
}

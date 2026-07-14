package com.cognologix.fpa.customer.dto;

import com.cognologix.fpa.customer.domain.CommercialTerms;
import com.cognologix.fpa.customer.domain.Customer;
import com.cognologix.fpa.customer.domain.CustomerProjectCode;
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
        boolean internal,
        CommercialTermsResponse commercialTerms,
        List<ProjectCodeResponse> projectCodes
) {
    public static CustomerDetailResponse from(
            Customer c,
            CommercialTerms commercialTerms,
            List<CustomerProjectCode> projectCodes) {
        return new CustomerDetailResponse(
                c.getId(), c.getCustomerCode(), c.getCustomerName(),
                c.getZohoBooksCustomerRef(), c.getRelationshipOwnerEmployeeId(),
                c.getLifecycleStatus(),
                c.isInternal(),
                commercialTerms != null ? CommercialTermsResponse.from(commercialTerms) : null,
                projectCodes.stream().map(ProjectCodeResponse::from).toList());
    }
}

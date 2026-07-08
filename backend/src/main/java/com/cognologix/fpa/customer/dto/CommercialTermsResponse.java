package com.cognologix.fpa.customer.dto;

import com.cognologix.fpa.customer.domain.CommercialTerms;

public record CommercialTermsResponse(int dsoDays) {
    public static CommercialTermsResponse from(CommercialTerms t) {
        return new CommercialTermsResponse(t.getDsoDays());
    }
}

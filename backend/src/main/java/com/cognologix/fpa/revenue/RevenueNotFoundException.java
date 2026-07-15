package com.cognologix.fpa.revenue;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RevenueNotFoundException extends RuntimeException {

    public RevenueNotFoundException(String message) {
        super(message);
    }
}

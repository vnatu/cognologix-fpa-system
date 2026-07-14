package com.cognologix.fpa.customer;

public class CustomerBadRequestException extends RuntimeException {
    public CustomerBadRequestException(String message) {
        super(message);
    }
}

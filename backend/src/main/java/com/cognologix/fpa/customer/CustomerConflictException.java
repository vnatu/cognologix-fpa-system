package com.cognologix.fpa.customer;

public class CustomerConflictException extends RuntimeException {
    public CustomerConflictException(String message) {
        super(message);
    }
}

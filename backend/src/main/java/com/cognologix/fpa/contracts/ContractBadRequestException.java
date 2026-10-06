package com.cognologix.fpa.contracts;

public class ContractBadRequestException extends RuntimeException {
    public ContractBadRequestException(String message) {
        super(message);
    }
}

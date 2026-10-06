package com.cognologix.fpa.bankrecon;

/** Statement account has no active row in recon_account_mapping. */
public class UnmappedAccountException extends RuntimeException {

    public UnmappedAccountException(String message) {
        super(message);
    }
}

package com.cognologix.fpa.general;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class GeneralBadRequestException extends RuntimeException {

    public GeneralBadRequestException(String message) {
        super(message);
    }
}

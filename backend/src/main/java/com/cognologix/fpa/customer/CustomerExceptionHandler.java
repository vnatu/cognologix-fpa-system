package com.cognologix.fpa.customer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.cognologix.fpa.customer")
public class CustomerExceptionHandler {

    @ExceptionHandler(CustomerBadRequestException.class)
    public ResponseEntity<Map<String, String>> badRequest(CustomerBadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }
}

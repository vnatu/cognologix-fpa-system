package com.cognologix.fpa.contracts;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackageClasses = ContractController.class)
public class ContractExceptionHandler {

    @ExceptionHandler(ContractBadRequestException.class)
    public ResponseEntity<Map<String, String>> badRequest(ContractBadRequestException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ContractNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ContractNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}

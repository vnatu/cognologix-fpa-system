package com.cognologix.fpa.general;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.cognologix.fpa.general")
public class GeneralExceptionHandler {

    @ExceptionHandler(GeneralBadRequestException.class)
    public ResponseEntity<Map<String, String>> badRequest(GeneralBadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}

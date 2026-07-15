package com.cognologix.fpa.revenue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice(basePackages = "com.cognologix.fpa.revenue")
public class RevenueExceptionHandler {

    @ExceptionHandler(RevenueNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(RevenueNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RevenueBadRequestException.class)
    public ResponseEntity<Map<String, String>> badRequest(RevenueBadRequestException ex) {
        log.warn("Revenue module bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}

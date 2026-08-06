package com.medstock.auth.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuthException(AuthException ex) {
        return buildResponse(ex.getStatus(), ex.getMessage());
    }

    /**
     * The username pre-check in register() closes the common case, but two concurrent
     * registrations of the same username can both pass it and let the database's unique
     * constraint reject the second insert. Without this handler that fell through to the generic
     * one below, answering 500 with the raw Hibernate constraint-violation text.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Registration rejected by a database constraint", ex);
        return buildResponse(HttpStatus.CONFLICT, "Username already exists");
    }

    /**
     * Last resort. The message is deliberately generic: the exception text here is unmapped
     * internals (SQL, stack details) and does not belong in a client response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception in auth-service", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong, please try again");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}
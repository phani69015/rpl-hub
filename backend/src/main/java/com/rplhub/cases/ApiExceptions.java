package com.rplhub.cases;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptions {

    /** 409 carrying the current server state so a client can resolve the conflict. */
    static class ConflictException extends RuntimeException {
        final transient Object server;
        ConflictException(String message, Object server) {
            super(message);
            this.server = server;
        }
    }

    /** 400 with a user-readable message. */
    static class InvalidException extends RuntimeException {
        InvalidException(String message) { super(message); }
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(ConflictException e) {
        var body = new java.util.HashMap<String, Object>();
        body.put("error", e.getMessage());
        if (e.server != null) body.put("server", e.server);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidException.class)
    ResponseEntity<Map<String, String>> invalid(InvalidException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }
}

package com.marianna.gateway.exception;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail handleNotFound(PaymentNotFoundException ex) {
        log.error("Payment not found", ex);
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        p.setTitle("Payment Not Found");
        return p;
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        log.error("Illegal state transition", ex);
        p.setTitle("Invalid State Transition");
        return p;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        log.error("Invalid request", ex);
        p.setTitle("Invalid Request");
        return p;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        log.error("Data integrity violation", ex);
        p.setTitle("Idempotency key already exists");
        return p;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        log.error("Concurrent modification", ex);
        p.setTitle("Concurrent modification");
        return p;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        p.setTitle("Internal Server Error");
        return p;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage).collect(Collectors.joining(", "));
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        p.setTitle("Validation Failed");
        return ResponseEntity.badRequest().body(p);
    }
}

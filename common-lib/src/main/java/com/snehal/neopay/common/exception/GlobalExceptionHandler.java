package com.snehal.neopay.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateResources(DuplicateResourceException ex,
                                                                  HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRuleViolation(BusinessRuleViolationException ex,
                                                                     HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request) {
        String errorCode = ex.getResourceName().toUpperCase() + "_NOT_FOUND";
        return respond(HttpStatus.NOT_FOUND, errorCode, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();

        ProblemDetail problem = ApiProblem.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed", fieldErrors);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitException(RateLimitException ex,
                                                                  HttpServletRequest request) {
        ProblemDetail problem = ApiProblem.of(HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED", ex.getMessage(), URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Remaining", "0")
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .header("X-RateLimit-Reset", String.valueOf(
                        Instant.now().plusSeconds(ex.getRetryAfterSeconds()).getEpochSecond()))
                .body(problem);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidStateTransition(InvalidStateTransitionException ex,
                                                                      HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(IdempotencyConflictException ex,
                                                                   HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage(), request);
    }

    @ExceptionHandler(RiskRejectedException.class)
    public ResponseEntity<ProblemDetail> handleRiskRejected(RiskRejectedException ex,
                                                            HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, ex.getReason(), ex.getMessage(), request);
    }

    @ExceptionHandler(StepUpRequiredException.class)
    public ResponseEntity<ProblemDetail> handleStepUp(StepUpRequiredException ex,
                                                      HttpServletRequest request) {
        return respond(HttpStatus.PRECONDITION_REQUIRED, "STEP_UP_REQUIRED",
                ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                      HttpServletRequest request) {
        log.error("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "The request could not be completed due to a data conflict", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        log.warn("Unreadable request body on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY",
                "Request body is missing or could not be parsed", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Parameter '" + ex.getName() + "' has an invalid value", request);
    }

    @ExceptionHandler({
            org.springframework.dao.OptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class
    })
    public ResponseEntity<ProblemDetail> handleConcurrentModification(Exception ex,
                                                                     HttpServletRequest request) {
        log.warn("Concurrent modification on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The resource was modified by another request; retry", request);
    }

    @ExceptionHandler({
            org.springframework.web.ErrorResponseException.class,
            org.springframework.web.server.ResponseStatusException.class,
            org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class
    })
    public ResponseEntity<ProblemDetail> handleSpringErrorResponse(Exception ex,
                                                                   HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String detail = null;
        if (ex instanceof org.springframework.web.ErrorResponse error) {
            status = HttpStatus.valueOf(error.getStatusCode().value());
            if (error.getBody() != null && error.getBody().getDetail() != null) {
                detail = error.getBody().getDetail();
            }
        }
        return respond(status, status.name(),
                detail == null ? status.getReasonPhrase() : detail, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request);
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, String errorCode,
                                                         String detail, HttpServletRequest request) {
        ProblemDetail problem = ApiProblem.of(status, errorCode, detail,
                request == null ? null : URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(problem);
    }
}

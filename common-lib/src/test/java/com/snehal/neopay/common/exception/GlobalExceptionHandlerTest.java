package com.snehal.neopay.common.exception;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");

    @Test
    void duplicateResourceMapsToConflict() {
        ResponseEntity<ProblemDetail> response = handler.handleDuplicateResources(
                new DuplicateResourceException("ORDER", "duplicate"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ORDER", response.getBody().getProperties().get("errorCode"));
    }

    @Test
    void businessRuleViolationMapsToBadRequest() {
        ResponseEntity<ProblemDetail> response = handler.handleBusinessRuleViolation(
                new BusinessRuleViolationException("ORDER_NOT_PAYABLE", "nope"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ORDER_NOT_PAYABLE", response.getBody().getProperties().get("errorCode"));
    }

    @Test
    void notFoundDerivesCodeFromResourceName() {
        ResponseEntity<ProblemDetail> response = handler.handleNotFound(
                new ResourceNotFoundException("Payment", "abc"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("PAYMENT_NOT_FOUND", response.getBody().getProperties().get("errorCode"));
    }

    @Test
    void rateLimitAddsRetryHeaders() {
        ResponseEntity<ProblemDetail> response = handler.handleRateLimitException(
                new RateLimitException("rate limited", 30), request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("30", response.getHeaders().getFirst("Retry-After"));
    }

    @Test
    void invalidTransitionMapsToConflict() {
        ResponseEntity<ProblemDetail> response = handler.handleInvalidStateTransition(
                new InvalidStateTransitionException("CREATED", "SETTLE"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("INVALID_STATE_TRANSITION",
                response.getBody().getProperties().get("errorCode"));
    }

    @Test
    void idempotencyConflictMapsToConflict() {
        ResponseEntity<ProblemDetail> response = handler.handleIdempotencyConflict(
                new IdempotencyConflictException("in progress"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("IDEMPOTENCY_CONFLICT", response.getBody().getProperties().get("errorCode"));
    }

    @Test
    void dataIntegrityMapsToConflict() {
        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DATA_INTEGRITY_VIOLATION",
                response.getBody().getProperties().get("errorCode"));
    }

    @Test
    void unreadableBodyMapsToBadRequest() {
        ResponseEntity<ProblemDetail> response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("bad", (org.springframework.http.HttpInputMessage) null),
                request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MALFORMED_REQUEST_BODY",
                response.getBody().getProperties().get("errorCode"));
    }

    @Test
    void typeMismatchMapsToBadRequest() {
        ResponseEntity<ProblemDetail> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("x", String.class, "page", null, null),
                request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_PARAMETER", response.getBody().getProperties().get("errorCode"));
    }

    @Test
    void unexpectedMapsToInternalError() {
        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(
                new RuntimeException("boom"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getProperties().get("errorCode"));
        assertNotNull(response.getBody().getProperties().get("timestamp"));
    }

    @Test
    void riskRejectedMapsToForbiddenWithReasonCode() {
        ResponseEntity<ProblemDetail> response = handler.handleRiskRejected(
                new RiskRejectedException("SANCTIONS_HIT"), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("SANCTIONS_HIT", response.getBody().getProperties().get("errorCode"));
    }

    @Test
    void stepUpMapsToPreconditionRequired() {
        ResponseEntity<ProblemDetail> response = handler.handleStepUp(
                new StepUpRequiredException(), request);

        assertEquals(HttpStatus.PRECONDITION_REQUIRED, response.getStatusCode());
        assertEquals("STEP_UP_REQUIRED", response.getBody().getProperties().get("errorCode"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validationReportsEveryFieldErrorAndInstance() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "amount", "must be positive"));
        binding.addError(new FieldError("request", "currency", null));

        ResponseEntity<ProblemDetail> response = handler.handleValidation(
                new MethodArgumentNotValidException(null, binding), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_FAILED", response.getBody().getProperties().get("errorCode"));
        assertEquals("/v1/payments", response.getBody().getInstance().getPath());

        List<Map<String, String>> errors =
                (List<Map<String, String>>) response.getBody().getProperties().get("fieldErrors");
        assertEquals(2, errors.size());
        assertEquals("amount", errors.get(0).get("field"));
        assertEquals("must be positive", errors.get(0).get("message"));
        assertEquals("invalid", errors.get(1).get("message"));
    }

    @Test
    void springErrorResponsePreservesStatusAndExplicitDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleSpringErrorResponse(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "no such payment"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOT_FOUND", response.getBody().getProperties().get("errorCode"));
        assertEquals("no such payment", response.getBody().getDetail());
    }

    @Test
    void springErrorResponseFallsBackToReasonPhraseWhenDetailMissing() {
        ResponseEntity<ProblemDetail> response = handler.handleSpringErrorResponse(
                new ResponseStatusException(HttpStatus.CONFLICT), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Conflict", response.getBody().getDetail());
    }

    @Test
    void springErrorResponseDefaultsToBadRequestForPlainExceptions() {
        ResponseEntity<ProblemDetail> response = handler.handleSpringErrorResponse(
                new RuntimeException("boom"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Bad Request", response.getBody().getDetail());
    }

    @Test
    void missingRequestOmitsInstanceInsteadOfThrowing() {
        ResponseEntity<ProblemDetail> response = handler.handleDuplicateResources(
                new DuplicateResourceException("ORDER", "duplicate"), null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNull(response.getBody().getInstance());
    }
}

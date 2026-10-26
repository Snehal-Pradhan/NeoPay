package com.snehal.neopay.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * RFC 9457 problem details with backwards-compatible extension members.
 *
 * Standard members: type, title, status, detail, instance.
 * Extension members: errorCode, timestamp, fieldErrors.
 */
public final class ApiProblem {

    private ApiProblem() {
    }

    public static ProblemDetail of(HttpStatus status, String errorCode, String detail) {
        return build(status, errorCode, detail, null, null);
    }

    public static ProblemDetail of(HttpStatus status, String errorCode, String detail, URI instance) {
        return build(status, errorCode, detail, null, instance);
    }

    public static ProblemDetail of(HttpStatus status, String errorCode, String detail,
                                   List<Map<String, String>> fieldErrors) {
        return build(status, errorCode, detail, fieldErrors, null);
    }

    private static ProblemDetail build(HttpStatus status, String errorCode, String detail,
                                       List<Map<String, String>> fieldErrors, URI instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("about:blank"));
        if (instance != null) {
            problem.setInstance(instance);
        }
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("timestamp", Instant.now().toString());
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return problem;
    }
}

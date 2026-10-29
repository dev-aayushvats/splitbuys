package com.splitbuys.backend.common.web;

import com.splitbuys.backend.common.error.ConflictException;
import com.splitbuys.backend.common.error.DependencyFailureException;
import com.splitbuys.backend.common.error.DomainException;
import com.splitbuys.backend.common.error.InvalidRequestException;
import com.splitbuys.backend.common.error.NotFoundException;
import com.splitbuys.backend.common.error.RateLimitedException;
import com.splitbuys.backend.common.error.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place where exceptions become HTTP. Every response is an RFC 9457 problem document with a stable
 * {@code code} and the request id. {@code error} repeats {@code detail} for older clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> domain(DomainException e, WebRequest request) {
        HttpStatus status;
        if (e instanceof NotFoundException) {
            status = HttpStatus.NOT_FOUND;
        } else if (e instanceof InvalidRequestException) {
            status = HttpStatus.BAD_REQUEST;
        } else if (e instanceof ConflictException) {
            status = HttpStatus.CONFLICT;
        } else if (e instanceof UnauthorizedException) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (e instanceof RateLimitedException) {
            status = HttpStatus.TOO_MANY_REQUESTS;
        } else if (e instanceof DependencyFailureException) {
            log.error("Dependency failure ({})", e.code(), e);
            status = HttpStatus.BAD_GATEWAY;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return respond(status, e.code(), e.getMessage(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Object> staleWrite(ObjectOptimisticLockingFailureException e, WebRequest request) {
        return respond(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "This was changed by someone else. Reload and try again.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> integrity(DataIntegrityViolationException e, WebRequest request) {
        log.warn("Data integrity violation", e);
        return respond(HttpStatus.CONFLICT, "CONFLICT", "That change conflicts with existing data.", request);
    }

    /** Last resort: never leak internals, but log them with the request id so they can be found. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception e, WebRequest request) {
        log.error("Unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on our side.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("Invalid request");
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                           HttpHeaders headers, HttpStatusCode status,
                                                                           WebRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request", request);
    }

    /** Spring's own MVC errors (bad JSON, wrong method, type mismatch, ...) all pass through here. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail p ? p : ProblemDetail.forStatus(statusCode);
        if (problem.getDetail() == null || statusCode.is5xxServerError()) {
            problem.setDetail(statusCode.is4xxClientError() ? "Invalid request" : "Something went wrong on our side.");
        }
        if (statusCode.value() == 400 && ex instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
            problem.setDetail("Malformed request body");
        }
        enrich(problem, statusCode.is4xxClientError() ? "INVALID_REQUEST" : "INTERNAL_ERROR");
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private ResponseEntity<Object> respond(HttpStatus status, String code, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        enrich(problem, code);
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static void enrich(ProblemDetail problem, String defaultCode) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
            problem.setProperty("code", defaultCode);
        }
        problem.setProperty("error", problem.getDetail());
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
    }
}

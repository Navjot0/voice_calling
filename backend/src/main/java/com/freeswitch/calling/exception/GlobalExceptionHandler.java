package com.freeswitch.calling.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * Central place where every exception the API can produce - call control and
 * voice-user provisioning alike - is translated into a stable {@link ApiError}
 * JSON body. Internal exception messages and stack traces (in particular
 * anything coming out of the ESL client) are never returned to callers - they
 * are logged instead.
 *
 * <p>Voice-user provisioning exceptions are handled here rather than in a
 * separate advice class: Spring only applies one {@code @RestControllerAdvice}
 * catch-all ({@code Exception.class}) per exception, so two such classes would
 * compete rather than combine. Extending this one keeps a single, unambiguous
 * source of truth for every error response the API returns.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Invalid request");
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Malformed request body", request);
    }

    @ExceptionHandler(InvalidCallRequestException.class)
    public ResponseEntity<ApiError> handleInvalidCall(InvalidCallRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(CallNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(CallNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "CALL_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(FreeSwitchConnectionException.class)
    public ResponseEntity<ApiError> handleFsConnection(FreeSwitchConnectionException ex, HttpServletRequest request) {
        log.error("FreeSWITCH connection error on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "FREESWITCH_UNAVAILABLE",
                "Voice platform is temporarily unavailable", request);
    }

    @ExceptionHandler(FreeSwitchOperationException.class)
    public ResponseEntity<ApiError> handleFsOperation(FreeSwitchOperationException ex, HttpServletRequest request) {
        // Shared by call origination and directory provisioning/reload failures,
        // so the message stays generic rather than assuming which one failed.
        log.error("FreeSWITCH operation failed on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_GATEWAY, "FREESWITCH_OPERATION_FAILED",
                "The requested FreeSWITCH operation could not be completed", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("Invalid request");
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request);
    }

    @ExceptionHandler(InvalidVoiceUserExtensionException.class)
    public ResponseEntity<ApiError> handleInvalidVoiceUserExtension(InvalidVoiceUserExtensionException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(VoiceUserAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleVoiceUserAlreadyExists(VoiceUserAlreadyExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "VOICE_USER_ALREADY_EXISTS", ex.getMessage(), request);
    }

    @ExceptionHandler(VoiceUserNotFoundException.class)
    public ResponseEntity<ApiError> handleVoiceUserNotFound(VoiceUserNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "VOICE_USER_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(ProtectedVoiceUserException.class)
    public ResponseEntity<ApiError> handleProtectedVoiceUser(ProtectedVoiceUserException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "VOICE_USER_PROTECTED", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoStaticResource(NoResourceFoundException ex, HttpServletRequest request) {
        // Thrown for any request that doesn't match a controller mapping or a
        // static resource - most commonly a browser's automatic /favicon.ico
        // request, since this API serves no static content. A routine 404,
        // not an application error, so it's logged at DEBUG rather than the
        // ERROR + stack trace the Exception.class catch-all below would give it.
        log.debug("No resource for {}", request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error handling {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message, HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), error, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}

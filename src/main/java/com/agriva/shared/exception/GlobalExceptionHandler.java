package com.agriva.shared.exception;

import com.agriva.shared.models.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global HTTP error mapping.
 * <p>
 * The public {@code handleXxx} methods are all invoked reflectively by
 * Spring's {@code ExceptionHandlerExceptionResolver} via the
 * {@code @ExceptionHandler} annotation — they have no direct call site
 * anywhere in the codebase. The class-level {@code @SuppressWarnings("unused")}
 * keeps IDE / static-analysis noise out of the build; it does not affect
 * behavior.
 */
@RestControllerAdvice
@Slf4j
@SuppressWarnings("unused")
public class GlobalExceptionHandler {

    private static final String MDC_KEY = "requestId";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, WebRequest r) {
        log.warn("Business exception: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), r);
    }

    @ExceptionHandler(KafkaUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleKafkaUnavailable(
            KafkaUnavailableException ex, WebRequest r) {
        log.warn("Kafka unavailable: {}", ex.getMessage());
        HttpHeaders h = new HttpHeaders();
        h.set("Retry-After", "30");
        return buildWithHeaders(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), r, h);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, WebRequest r) {
        log.warn("Invalid credentials: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), r);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex, WebRequest r) {
        log.warn("Invalid token: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), r);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, WebRequest r) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), r);
    }

    @ExceptionHandler({IncomeNotFoundException.class, ExpenseNotFoundException.class,
            InvestmentNotFoundException.class, TransactionNotFoundException.class,
            PersonnelNotFoundException.class, TaskNotFoundException.class,
            SalaryPaymentNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleSubclassNotFound(RuntimeException ex, WebRequest r) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), r);
    }

    @ExceptionHandler(InsufficientPondStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientPondStock(
            InsufficientPondStockException ex, WebRequest r) {
        Map<String, String> errors = new HashMap<>();
        errors.put("species",   ex.getSpecies());
        errors.put("requested", String.valueOf(ex.getRequested()));
        errors.put("available", String.valueOf(ex.getAvailable()));
        log.warn("Insufficient pond stock: {}", ex.getMessage());
        return buildWithErrors(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), r, errors);
    }

    @ExceptionHandler({DuplicateIncomeException.class, DuplicateExpenseException.class,
            DuplicatePersonnelException.class, InvestmentLimitExceededException.class,
            NoActiveInvestmentException.class, InvalidTaskStateException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, WebRequest r) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), r);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex, WebRequest r) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), r);
    }

    @ExceptionHandler(ExcelProcessingException.class)
    public ResponseEntity<ErrorResponse> handleExcelProcessing(ExcelProcessingException ex, WebRequest r) {
        log.warn("Excel processing error: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), r);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest r) {
        log.warn("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Access denied", r);
    }

    @ExceptionHandler(PersonnelAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePersonnelAccessDenied(PersonnelAccessDeniedException ex, WebRequest r) {
        return build(HttpStatus.FORBIDDEN, ex.getReason(), r);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, WebRequest r) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return build(status, ex.getReason(), r);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex, WebRequest r) {
        HttpHeaders h = new HttpHeaders();
        h.set("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        return buildWithHeaders(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), r, h);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest r) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = err instanceof org.springframework.validation.FieldError fe
                    ? fe.getField() : "_";
            errors.put(field, err.getDefaultMessage());
        });
        return buildWithErrors(r, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest r) {
        String message = "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        return build(HttpStatus.BAD_REQUEST, message, r);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex, WebRequest r) {
        String message = "Required part '" + ex.getRequestPartName() + "' is not present";
        return build(HttpStatus.BAD_REQUEST, message, r);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex, WebRequest r) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the maximum allowed size", r);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, WebRequest r) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), r);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest r, HttpServletRequest request) {
        String correlationId = MDC.get(MDC_KEY);
        if (correlationId == null) correlationId = UUID.randomUUID().toString();
        log.error("Unhandled exception [requestId={}] on {}", correlationId, request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal error occurred. Reference: " + correlationId, r);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, WebRequest r) {
        return buildWithHeaders(status, message, r, null);
    }

    private ResponseEntity<ErrorResponse> buildWithHeaders(HttpStatus status, String message,
                                                           WebRequest r, HttpHeaders headers) {
        String requestId = MDC.get(MDC_KEY);
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(), status.value(), message,
                r.getDescription(false).replace("uri=", ""), requestId, null);
        ResponseEntity.BodyBuilder b = ResponseEntity.status(status);
        if (headers != null) b.headers(headers);
        return b.body(body);
    }

    private ResponseEntity<ErrorResponse> buildWithErrors(WebRequest r, Map<String, String> errors) {
        String requestId = MDC.get(MDC_KEY);
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Validation failed",
                r.getDescription(false).replace("uri=", ""), requestId, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InsufficientIngredientQuantityException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientIngredient(
            InsufficientIngredientQuantityException ex, WebRequest r) {
        Map<String, String> errors = new HashMap<>();
        errors.put("ingredient", ex.getIngredientName());
        errors.put("required", ex.getRequired() != null ? ex.getRequired().toPlainString() : "0");
        errors.put("available", ex.getAvailable() != null ? ex.getAvailable().toPlainString() : "0");
        errors.put("unit", ex.getUnit());
        log.warn("Insufficient ingredient: {}", ex.getMessage());
        return buildWithErrors(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), r, errors);
    }

    @ExceptionHandler(InsufficientFeedStoreException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFeedStore(
            InsufficientFeedStoreException ex, WebRequest r) {
        Map<String, String> errors = new HashMap<>();
        errors.put("requested", ex.getRequested() != null ? ex.getRequested().toPlainString() : "0");
        errors.put("available", ex.getAvailable() != null ? ex.getAvailable().toPlainString() : "0");
        errors.put("unit", ex.getUnit());
        log.warn("Insufficient feed store: {}", ex.getMessage());
        return buildWithErrors(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), r, errors);
    }

    @ExceptionHandler(UnregisteredConsumerTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnregisteredConsumerType(
            UnregisteredConsumerTypeException ex, WebRequest r) {
        log.warn("Unregistered consumer type: {}", ex.getConsumerType());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), r);
    }

    @ExceptionHandler(ConsumerNotFoundInDomainException.class)
    public ResponseEntity<ErrorResponse> handleConsumerNotFound(
            ConsumerNotFoundInDomainException ex, WebRequest r) {
        log.warn("Consumer not found: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), r);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupported(UnsupportedOperationException ex, WebRequest r) {
        log.info("Unsupported operation: {}", ex.getMessage());
        return build(HttpStatus.NOT_IMPLEMENTED, ex.getMessage(), r);
    }

    private ResponseEntity<ErrorResponse> buildWithErrors(HttpStatus status, String message,
                                                          WebRequest r, Map<String, String> errors) {
        String requestId = MDC.get(MDC_KEY);
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(), status.value(), message,
                r.getDescription(false).replace("uri=", ""), requestId, errors);
        return ResponseEntity.status(status).body(body);
    }
}
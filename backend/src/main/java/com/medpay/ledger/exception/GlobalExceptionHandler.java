package com.medpay.ledger.exception;

import com.medpay.ledger.dto.ErrorResponse;
import com.medpay.ledger.dto.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> PHI_FIELDS =
            Set.of("memberReference", "diagnosisCode", "serviceCode");

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDetail(
                        fe.getField(),
                        isPhiField(fe.getField()) ? null : fe.getRejectedValue(),
                        fe.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(Instant.now(), 400, ErrorCode.VALIDATION_FAILED,
                        "Request validation failed", path(request), fieldErrors, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, ErrorCode.VALIDATION_FAILED,
                        "Request body could not be parsed", path(request)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(Instant.now(), 400, ErrorCode.VALIDATION_FAILED,
                        "Request validation failed", path(request),
                        List.of(new FieldErrorDetail(ex.getName(),
                                isPhiField(ex.getName()) ? null : ex.getValue(),
                                "could not be parsed as " + typeNameOf(ex))),
                        null));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
                                                             HttpServletRequest request) {
        if (IDEMPOTENCY_KEY_HEADER.equalsIgnoreCase(ex.getHeaderName())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(400, ErrorCode.MISSING_IDEMPOTENCY_KEY,
                            "An Idempotency-Key header containing a UUID is required",
                            path(request)));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, ErrorCode.VALIDATION_FAILED,
                        "Missing required header " + ex.getHeaderName(), path(request)));
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ResponseEntity<ErrorResponse> handleMissingIdempotencyKey(
            MissingIdempotencyKeyException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, ErrorCode.MISSING_IDEMPOTENCY_KEY,
                        ex.getMessage(), path(request)));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex,
                                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, ErrorCode.INVALID_CREDENTIALS,
                        "Invalid email or password", path(request)));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex,
                                                        HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, ErrorCode.ACCOUNT_DISABLED,
                        "This account is disabled", path(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, ErrorCode.FORBIDDEN,
                        "Insufficient role for this operation", path(request)));
    }

    @ExceptionHandler(ClaimNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleClaimNotFound(ClaimNotFoundException ex,
                                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, ErrorCode.CLAIM_NOT_FOUND,
                        ex.getMessage(), path(request)));
    }

    @ExceptionHandler(DuplicateClaimException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateClaim(DuplicateClaimException ex,
                                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, ErrorCode.DUPLICATE_CLAIM, ex.getMessage(),
                        path(request),
                        Map.of("existingClaimUuid", ex.getExistingClaimUuid().toString(),
                                "fingerprint", ex.getFingerprint())));
    }

    @ExceptionHandler(LineItemSumMismatchException.class)
    public ResponseEntity<ErrorResponse> handleLineSum(LineItemSumMismatchException ex,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(422, ErrorCode.LINE_SUM_MISMATCH, ex.getMessage(),
                        path(request),
                        Map.of("headerBilledAmount", ex.getHeaderAmount().toPlainString(),
                                "computedLineSum", ex.getLineSum().toPlainString(),
                                "difference", ex.difference().toPlainString())));
    }

    @ExceptionHandler(UnknownServiceCodeException.class)
    public ResponseEntity<ErrorResponse> handleUnknownServiceCode(UnknownServiceCodeException ex,
                                                                  HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(422, ErrorCode.UNKNOWN_SERVICE_CODE, ex.getMessage(),
                        path(request),
                        Map.of("serviceCode", ex.getServiceCode(),
                                "serviceDate", ex.getServiceDate().toString())));
    }

    @ExceptionHandler(UnknownProviderException.class)
    public ResponseEntity<ErrorResponse> handleUnknownProvider(UnknownProviderException ex,
                                                               HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(422, ErrorCode.UNKNOWN_PROVIDER, ex.getMessage(),
                        path(request), Map.of("providerNpi", ex.getProviderNpi())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        log.error("Unhandled exception [correlationId={}] on {}", correlationId, path(request), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, ErrorCode.INTERNAL_ERROR,
                        "An unexpected error occurred", path(request),
                        Map.of("correlationId", correlationId)));
    }

    private static String typeNameOf(MethodArgumentTypeMismatchException ex) {
        return ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "the expected type";
    }

    private static boolean isPhiField(String field) {
        return PHI_FIELDS.stream().anyMatch(field::endsWith);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI();
    }
}

package com.medpay.ledger.exception;

import com.medpay.ledger.dto.ErrorResponse;
import com.medpay.ledger.dto.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every non-2xx body in the system is produced here (FR-026, §5.6). No endpoint
 * returns a bare string or a Spring Boot default error body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Fields whose submitted value must never be echoed back. The match is on
     * suffix, so {@code lines[0].diagnosisCode} is caught as well as the bare
     * name (FR-030).
     */
    private static final Set<String> PHI_FIELDS =
            Set.of("memberReference", "diagnosisCode", "serviceCode");

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
                .body(new ErrorResponse(java.time.Instant.now(), 400,
                        ErrorCode.VALIDATION_FAILED, "Request validation failed",
                        path(request), fieldErrors, null));
    }

    /**
     * Unknown email and wrong password arrive here identically — {@code
     * AuthService} raises the same exception for both, and this handler adds no
     * distinguishing detail (NFR-004).
     */
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

    /**
     * A {@code @PreAuthorize} rejection at the handler surfaces as an exception
     * rather than reaching {@code RestAccessDeniedHandler}, so the same envelope
     * has to be produced in both places.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, ErrorCode.FORBIDDEN,
                        "Insufficient role for this operation", path(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        // The trace is logged against the correlation id and never returned.
        log.error("Unhandled exception [correlationId={}] on {}", correlationId, path(request), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, ErrorCode.INTERNAL_ERROR,
                        "An unexpected error occurred", path(request),
                        Map.of("correlationId", correlationId)));
    }

    private static boolean isPhiField(String field) {
        return PHI_FIELDS.stream().anyMatch(field::endsWith);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI();
    }
}

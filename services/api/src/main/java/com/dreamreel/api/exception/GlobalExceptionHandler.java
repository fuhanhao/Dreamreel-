package com.dreamreel.api.exception;

import com.dreamreel.api.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.apache.catalina.connector.ClientAbortException;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Shown when a third-party provider rejects the configured API key. Must not be HTTP 401 — the web client treats 401 as login expiry. */
    static final String PROVIDER_AUTH_MESSAGE = "第三方 API Key 无效或已过期，请在个人设置中更新后重试";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(false, Map.of("error", ex.getMessage()), ex.getMessage(), java.time.Instant.now()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleIllegalState(IllegalStateException ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "操作失败"
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, Map.of("error", message), message, java.time.Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "请求参数有误"
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, Map.of("error", message), message, java.time.Instant.now()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(false, Map.of("error", "无访问权限"), "无访问权限", java.time.Instant.now()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleDataIntegrity(DataIntegrityViolationException ex) {
        var detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        log.warn("Data integrity violation: {}", detail);
        // 并发入队撞「每项目仅一条 RUNNING」时给出可读文案（正常路径应已落 QUEUED）
        if (detail != null && detail.contains("uq_dramaforge_jobs_running_project")) {
            var msg = "已有任务执行中，请稍候或查看排队等待中的任务";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, Map.of("error", msg), msg, java.time.Instant.now()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, Map.of("error", "数据保存失败"), "数据保存失败", java.time.Instant.now()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(false, Map.of("error", "未授权"), "未授权", java.time.Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, Map.of("error", message), message, java.time.Instant.now()));
    }

    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    public void handleClientDisconnect(Exception ex) {
        log.debug("Client disconnected: {}", ex.getMessage());
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleRestClient(RestClientResponseException ex) {
        String message = extractUpstreamMessage(ex);
        int upstreamStatus = ex.getStatusCode().value();

        // Upstream TokenFree/ARK 401 must not become session-unauthorized; the SPA clears login on HTTP 401.
        if (upstreamStatus == 401 || upstreamStatus == 403 || isProviderAuthFailure(message)) {
            log.warn("Upstream provider auth failed (HTTP {}): {}", upstreamStatus, message);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, Map.of("error", PROVIDER_AUTH_MESSAGE), PROVIDER_AUTH_MESSAGE, java.time.Instant.now()));
        }

        if (message == null || message.isBlank()) {
            message = "上游服务请求失败 (HTTP " + upstreamStatus + ")";
        }
        HttpStatus status = HttpStatus.resolve(upstreamStatus);
        if (status == null || status.is1xxInformational() || status == HttpStatus.UNAUTHORIZED) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return ResponseEntity.status(status)
                .body(new ApiResponse<>(false, Map.of("error", message), message, java.time.Instant.now()));
    }

    static String extractUpstreamMessage(RestClientResponseException ex) {
        String message = ex.getResponseBodyAsString();
        if (message != null && message.contains("\"message\"")) {
            int start = message.indexOf("\"message\"");
            if (start >= 0) {
                int colon = message.indexOf(':', start);
                int quoteStart = message.indexOf('"', colon + 1);
                int quoteEnd = message.indexOf('"', quoteStart + 1);
                if (quoteStart >= 0 && quoteEnd > quoteStart) {
                    return message.substring(quoteStart + 1, quoteEnd);
                }
            }
        }
        return message;
    }

    static boolean isProviderAuthFailure(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("invalid token")
                || lower.contains("invalid api key")
                || lower.contains("incorrect api key")
                || lower.contains("authentication")
                || lower.contains("unauthorized")
                || message.contains("鉴权失败");
    }
}

package com.dreamreel.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsUpstream401InvalidTokenToBadRequest() {
        var body = "{\"error\":{\"message\":\"Invalid token (request id: abc)\"},\"message\":\"Invalid token (request id: abc)\"}";
        var ex = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        var response = handler.handleRestClient(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(GlobalExceptionHandler.PROVIDER_AUTH_MESSAGE, response.getBody().message());
        assertFalse(response.getBody().success());
    }

    @Test
    void mapsUpstream403ToBadRequest() {
        var ex = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                HttpHeaders.EMPTY,
                "{\"message\":\"Incorrect API key\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        var response = handler.handleRestClient(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(GlobalExceptionHandler.PROVIDER_AUTH_MESSAGE, response.getBody().message());
    }

    @Test
    void preservesNonAuthUpstreamStatus() {
        var ex = HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY,
                "Bad Gateway",
                HttpHeaders.EMPTY,
                "{\"message\":\"upstream timeout\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        var response = handler.handleRestClient(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("upstream timeout", response.getBody().message());
    }

    @Test
    void detectsProviderAuthFailureMessages() {
        assertTrue(GlobalExceptionHandler.isProviderAuthFailure("Invalid token (request id: x)"));
        assertTrue(GlobalExceptionHandler.isProviderAuthFailure("Incorrect API key provided"));
        assertFalse(GlobalExceptionHandler.isProviderAuthFailure("rate limit exceeded"));
    }
}

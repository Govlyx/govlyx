package com.Govlyx.AI.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

public class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void testHandleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE");
        WebRequest request = mock(WebRequest.class);

        ResponseEntity<ApiResponse<Object>> responseEntity = handler.handleMethodNotSupported(ex, request);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals("Method Not Allowed", responseEntity.getBody().getMessage());
    }
}

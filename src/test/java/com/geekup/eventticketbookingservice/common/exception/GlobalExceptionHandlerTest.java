package com.geekup.eventticketbookingservice.common.exception;

import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleAppException returns correct status and error response")
    void handleAppException_ReturnsCustomErrorCodeAndStatus() {
        AppException ex = new AppException(ErrorCode.VOUCHER_LIMIT_REACHED, "Voucher limit reached for campaign");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("VOUCHER_LIMIT_REACHED", response.getBody().getError().code());
        assertEquals("Voucher limit reached for campaign", response.getBody().getError().message());
    }

    @Test
    @DisplayName("handleValidationException returns 400 Bad Request with concatenated field errors")
    void handleValidationException_ReturnsValidationErrors() throws NoSuchMethodException {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "createBookingRequest");
        bindingResult.addError(new FieldError("createBookingRequest", "items", "must not be empty"));
        bindingResult.addError(new FieldError("createBookingRequest", "voucherCode", "invalid format"));

        Method method = this.getClass().getDeclaredMethod("setUp");
        MethodParameter parameter = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleValidationException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("VALIDATION_ERROR", response.getBody().getError().code());
        assertTrue(response.getBody().getError().message().contains("items: must not be empty"));
        assertTrue(response.getBody().getError().message().contains("voucherCode: invalid format"));
    }

    @Test
    @DisplayName("handleAuthenticationException returns 401 Unauthorized")
    void handleAuthenticationException_ReturnsUnauthorized() {
        AuthenticationException ex = new BadCredentialsException("Bad credentials provided");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAuthenticationException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("UNAUTHORIZED", response.getBody().getError().code());
        assertEquals("Unauthorized access", response.getBody().getError().message());
    }

    @Test
    @DisplayName("handleAccessDeniedException returns 403 Forbidden")
    void handleAccessDeniedException_ReturnsForbidden() {
        AccessDeniedException ex = new AccessDeniedException("Access is denied");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAccessDeniedException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("FORBIDDEN", response.getBody().getError().code());
        assertEquals("Forbidden access", response.getBody().getError().message());
    }

    @Test
    @DisplayName("handleException returns 500 Internal Server Error for unhandled exceptions")
    void handleException_ReturnsInternalServerError() {
        RuntimeException ex = new NullPointerException("Null pointer occurred in business logic");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getError().code());
        assertEquals("Internal server error", response.getBody().getError().message());
    }
}

package com.geekup.eventticketbookingservice.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    @DisplayName("ApiResponse.success creates successful response with data and null error")
    void success_CreatesSuccessResponse() {
        String testData = "Hello World";
        ApiResponse<String> response = ApiResponse.success(testData);

        assertTrue(response.isSuccess());
        assertEquals("Hello World", response.getData());
        assertNull(response.getError());
    }

    @Test
    @DisplayName("ApiResponse.error creates failure response with error and null data")
    void error_CreatesErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error("INVALID_INPUT", "Input validation failed");

        assertFalse(response.isSuccess());
        assertNull(response.getData());
        assertNotNull(response.getError());
        assertEquals("INVALID_INPUT", response.getError().code());
        assertEquals("Input validation failed", response.getError().message());
    }

    @Test
    @DisplayName("ApiResponse builder creates custom response")
    void builder_CreatesCustomResponse() {
        ApiResponse.ErrorResponse errorResponse = new ApiResponse.ErrorResponse("ERR_CODE", "Error message");
        ApiResponse<Integer> response = ApiResponse.<Integer>builder()
                .success(false)
                .data(123)
                .error(errorResponse)
                .build();

        assertFalse(response.isSuccess());
        assertEquals(123, response.getData());
        assertEquals("ERR_CODE", response.getError().code());
        assertEquals("Error message", response.getError().message());
    }

    @Test
    @DisplayName("ApiResponse getters, setters, equals, and hashCode work as expected")
    void gettersSettersAndEquals() {
        ApiResponse<String> response1 = ApiResponse.success("test");
        ApiResponse<String> response2 = ApiResponse.success("test");

        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
        assertTrue(response1.toString().contains("test"));

        response1.setData("updated");
        assertEquals("updated", response1.getData());

        response1.setSuccess(false);
        assertFalse(response1.isSuccess());

        ApiResponse.ErrorResponse err = new ApiResponse.ErrorResponse("CODE", "MSG");
        response1.setError(err);
        assertEquals(err, response1.getError());
        assertEquals("CODE", err.code());
        assertEquals("MSG", err.message());
    }
}

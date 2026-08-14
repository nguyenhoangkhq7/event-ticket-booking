package com.geekup.eventticketbookingservice.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Role Enum Unit Tests")
class RoleTest {

    @Test
    @DisplayName("Should contain defined Role enum values")
    void testRoleEnumValues() {
        assertEquals(2, Role.values().length);
        assertEquals(Role.CUSTOMER, Role.valueOf("CUSTOMER"));
        assertEquals(Role.ADMIN, Role.valueOf("ADMIN"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when valueOf is given an invalid name")
    void testRoleInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> Role.valueOf("INVALID_ROLE"));
    }
}

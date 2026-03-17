package com.aihiring.role;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoleTest {
    @Test
    void role_shouldHaveCorrectFields() {
        Role role = new Role();
        role.setName("TEST_ROLE");
        role.setDescription("Test role");

        assertEquals("TEST_ROLE", role.getName());
        assertEquals("Test role", role.getDescription());
    }
}

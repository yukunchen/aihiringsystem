package com.aihiring.user;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest {
    @Test
    void userRepository_shouldExist() {
        // Verify repository class can be loaded
        Class<?> repoClass = UserRepository.class;
        assertNotNull(repoClass);
        assertTrue(repoClass.getName().contains("UserRepository"));
    }
}

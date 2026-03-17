package com.aihiring.auth;

import com.aihiring.auth.dto.LoginRequest;
import com.aihiring.auth.dto.LogoutRequest;
import com.aihiring.auth.dto.RefreshRequest;
import com.aihiring.auth.dto.TokenResponse;
import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.security.JwtTokenProvider;
import com.aihiring.role.Role;
import com.aihiring.user.User;
import com.aihiring.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setPassword("encodedPassword");
        testUser.setEmail("test@example.com");
        testUser.setEnabled(true);

        Role role = new Role();
        role.setName("USER");
        testUser.setRoles(Set.of(role));
    }

    @Test
    void login_withValidCredentials_shouldReturnTokens() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any())).thenReturn("accessToken");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refreshToken");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        TokenResponse response = authService.login(request);

        // Assert
        assertNotNull(response);
        assertEquals("accessToken", response.getAccessToken());
        assertEquals("refreshToken", response.getRefreshToken());
        assertEquals(7200, response.getExpiresIn());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_withInvalidUsername_shouldThrowException() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("password");

        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void login_withInvalidPassword_shouldThrowException() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrongPassword");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void login_withDisabledUser_shouldThrowException() {
        // Arrange
        testUser.setEnabled(false);
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "encodedPassword")).thenReturn(true);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(403, exception.getCode());
    }

    @Test
    void refresh_withValidToken_shouldReturnNewTokens() {
        // Arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("validRefreshToken");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setTokenHash("hashedToken");
        refreshToken.setUser(testUser);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any())).thenReturn("newAccessToken");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("newRefreshToken");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        TokenResponse response = authService.refresh(request);

        // Assert
        assertNotNull(response);
        assertEquals("newAccessToken", response.getAccessToken());
        assertEquals("newRefreshToken", response.getRefreshToken());
    }

    @Test
    void refresh_withInvalidToken_shouldThrowException() {
        // Arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("invalidRefreshToken");

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refresh(request));
        assertEquals(401, exception.getCode());
    }

    @Test
    void refresh_withRevokedToken_shouldThrowException() {
        // Arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("revokedRefreshToken");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setTokenHash("hashedToken");
        refreshToken.setUser(testUser);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshToken.setRevoked(true);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refresh(request));
        assertEquals(401, exception.getCode());
    }

    @Test
    void refresh_withExpiredToken_shouldThrowException() {
        // Arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("expiredRefreshToken");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setTokenHash("hashedToken");
        refreshToken.setUser(testUser);
        refreshToken.setExpiresAt(LocalDateTime.now().minusDays(1));
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refresh(request));
        assertEquals(401, exception.getCode());
    }

    @Test
    void logout_withValidToken_shouldRevokeToken() {
        // Arrange
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refreshToken");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setTokenHash("hashedToken");
        refreshToken.setUser(testUser);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        authService.logout(request);

        // Assert
        assertTrue(refreshToken.isRevoked());
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void logout_withInvalidToken_shouldNotThrowException() {
        // Arrange
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("invalidRefreshToken");

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> authService.logout(request));
    }
}

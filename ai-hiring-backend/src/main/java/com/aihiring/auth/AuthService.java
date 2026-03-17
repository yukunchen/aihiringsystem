package com.aihiring.auth;

import com.aihiring.auth.dto.*;
import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.security.JwtTokenProvider;
import com.aihiring.user.User;
import com.aihiring.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new BusinessException(403, "Account is disabled");
        }

        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toList());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // Save refresh token
        RefreshToken token = new RefreshToken();
        token.setTokenHash(hashToken(refreshToken));
        token.setUser(user);
        token.setExpiresAt(java.time.LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(token);

        return new TokenResponse(accessToken, refreshToken, 7200);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hashToken(request.getRefreshToken()))
                .orElseThrow(() -> new BusinessException(401, "Invalid refresh token"));

        if (token.isRevoked() || token.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new BusinessException(401, "Refresh token expired or revoked");
        }

        User user = token.getUser();
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toList());

        // Revoke old token
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // Generate new tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        RefreshToken newToken = new RefreshToken();
        newToken.setTokenHash(hashToken(refreshToken));
        newToken.setUser(user);
        newToken.setExpiresAt(java.time.LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(newToken);

        return new TokenResponse(accessToken, refreshToken, 7200);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByTokenHash(hashToken(request.getRefreshToken()))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}

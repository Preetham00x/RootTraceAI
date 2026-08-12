package com.roottrace.user;

import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.security.JwtService;
import com.roottrace.common.security.SecurityProperties;
import com.roottrace.common.security.SecurityUser;
import com.roottrace.user.dto.LoginRequest;
import jakarta.servlet.http.Cookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                       SecurityProperties securityProperties) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.securityProperties = securityProperties;
    }

    @Transactional
    public AuthCookies login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmailAndNotDeleted(request.email())
                .orElseThrow(() -> new BadRequestException("User not found"));

        SecurityUser securityUser = new SecurityUser(user);
        
        String accessToken = jwtService.generateToken(securityUser);
        
        // Generate new refresh token
        String refreshTokenString = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusMillis(securityProperties.getRefreshTokenExpirationMs());
        
        RefreshToken refreshToken = new RefreshToken(user, refreshTokenString, expiryDate);
        refreshTokenRepository.save(refreshToken);

        return new AuthCookies(
                createCookie("access_token", accessToken, securityProperties.getAccessTokenExpirationMs() / 1000),
                createCookie("refresh_token", refreshTokenString, securityProperties.getRefreshTokenExpirationMs() / 1000)
        );
    }

    @Transactional
    public AuthCookies refresh(String requestRefreshToken) {
        if (requestRefreshToken == null || requestRefreshToken.isBlank()) {
            throw new BadRequestException("Refresh token is missing");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseThrow(() -> new BadRequestException("Refresh token not found"));

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new BadRequestException("Refresh token was expired. Please make a new signin request");
        }

        User user = refreshToken.getUser();
        
        // Delete old refresh token (rotation)
        refreshTokenRepository.delete(refreshToken);
        
        // Generate new tokens
        SecurityUser securityUser = new SecurityUser(user);
        String accessToken = jwtService.generateToken(securityUser);
        
        String newRefreshTokenString = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusMillis(securityProperties.getRefreshTokenExpirationMs());
        
        RefreshToken newRefreshToken = new RefreshToken(user, newRefreshTokenString, expiryDate);
        refreshTokenRepository.save(newRefreshToken);

        return new AuthCookies(
                createCookie("access_token", accessToken, securityProperties.getAccessTokenExpirationMs() / 1000),
                createCookie("refresh_token", newRefreshTokenString, securityProperties.getRefreshTokenExpirationMs() / 1000)
        );
    }

    @Transactional
    public AuthCookies logout(User user) {
        if (user != null) {
            refreshTokenRepository.deleteByUserId(user.getId());
        }
        
        // Return clearing cookies
        return new AuthCookies(
                createCookie("access_token", "", 0),
                createCookie("refresh_token", "", 0)
        );
    }

    private Cookie createCookie(String name, String value, long maxAgeSecs) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // Must be true in production, assuming https or localhost testing
        cookie.setPath("/");
        cookie.setMaxAge((int) maxAgeSecs);
        return cookie;
    }

    public record AuthCookies(Cookie accessCookie, Cookie refreshCookie) {}
}

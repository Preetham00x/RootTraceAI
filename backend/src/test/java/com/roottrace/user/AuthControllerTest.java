package com.roottrace.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.user.dto.LoginRequest;
import com.roottrace.user.dto.LoginRequest;
import com.roottrace.user.dto.UserResponse;
import com.roottrace.common.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import com.roottrace.common.security.CurrentUserService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.servlet.http.Cookie;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class AuthControllerTest {

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @DisplayName("should login and return cookies")
    void shouldLogin() throws Exception {
        LoginRequest request = new LoginRequest("test@test.com", "password");
        AuthService.AuthCookies cookies = new AuthService.AuthCookies(
                new Cookie("access_token", "test-access"),
                new Cookie("refresh_token", "test-refresh")
        );

        when(authService.login(any(LoginRequest.class))).thenReturn(cookies);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().value("access_token", "test-access"))
                .andExpect(cookie().value("refresh_token", "test-refresh"));
    }

    @Test
    @DisplayName("should refresh token and return new cookies")
    void shouldRefreshToken() throws Exception {
        AuthService.AuthCookies cookies = new AuthService.AuthCookies(
                new Cookie("access_token", "new-access"),
                new Cookie("refresh_token", "new-refresh")
        );

        when(authService.refresh(anyString())).thenReturn(cookies);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(cookie().value("access_token", "new-access"))
                .andExpect(cookie().value("refresh_token", "new-refresh"));
    }

    @Test
    @DisplayName("should reject refresh if no cookie provided")
    void shouldRejectRefreshWithoutCookie() throws Exception {
        when(authService.refresh(null)).thenThrow(new RuntimeException("Missing refresh token"));
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isInternalServerError()); // Or bad request depending on implementation
    }
}

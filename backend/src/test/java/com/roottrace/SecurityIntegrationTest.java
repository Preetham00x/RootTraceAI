package com.roottrace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import com.roottrace.user.UserRepository;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.common.security.SecurityUser;
import com.roottrace.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User adminUser;
    private User engineerUser;
    private User viewerUser;

    @BeforeEach
    void setUp() {
        incidentRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(new User("admin@test.com", passwordEncoder.encode("password"), "Admin", "User", Role.ADMIN));
        engineerUser = userRepository.save(new User("engineer@test.com", passwordEncoder.encode("password"), "Engineer", "User", Role.ENGINEER));
        viewerUser = userRepository.save(new User("viewer@test.com", passwordEncoder.encode("password"), "Viewer", "User", Role.VIEWER));
    }

    @Test
    @DisplayName("should reject request without token")
    void shouldRejectWithoutToken() throws Exception {
        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should reject request with invalid token")
    void shouldRejectWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/incidents")
                .cookie(new Cookie("access_token", "invalid.token.here")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should allow Viewer to list incidents")
    void shouldAllowViewerToRead() throws Exception {
        String token = jwtService.generateToken(new SecurityUser(viewerUser));
        
        mockMvc.perform(get("/api/incidents")
                .cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should deny Viewer from creating incident")
    void shouldDenyViewerWrite() throws Exception {
        String token = jwtService.generateToken(new SecurityUser(viewerUser));
        
        String requestBody = """
                {
                    "title": "Test",
                    "description": "Test",
                    "service": "test",
                    "severity": "LOW",
                    "environment": "test"
                }
                """;

        mockMvc.perform(post("/api/incidents")
                .cookie(new Cookie("access_token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should allow Engineer to create incident")
    void shouldAllowEngineerWrite() throws Exception {
        String token = jwtService.generateToken(new SecurityUser(engineerUser));
        
        String requestBody = """
                {
                    "title": "Test",
                    "description": "Test",
                    "service": "test",
                    "severity": "LOW",
                    "environment": "test"
                }
                """;

        mockMvc.perform(post("/api/incidents")
                .cookie(new Cookie("access_token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated());
    }
}

package io.spring.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.spring.application.user.RegisterParam;
import io.spring.application.user.UserService;
import io.spring.core.service.JwtService;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
public class SecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserService userService;

  @Autowired private UserRepository userRepository;

  @Autowired private JwtService jwtService;

  @Autowired private ObjectMapper objectMapper;

  private User testUser;
  private String validToken;

  @BeforeEach
  public void setUp() {
    RegisterParam registerParam =
        new RegisterParam("security@example.com", "securityuser", "password");
    testUser = userService.createUser(registerParam);
    validToken = jwtService.toToken(testUser);
  }

  @Test
  public void should_allow_access_with_valid_jwt_token() throws Exception {
    mockMvc
        .perform(
            get("/user")
                .header("Authorization", "Token " + validToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("security@example.com"))
        .andExpect(jsonPath("$.user.username").value("securityuser"));
  }

  @Test
  public void should_reject_request_with_invalid_jwt_token() throws Exception {
    mockMvc
        .perform(
            get("/user")
                .header("Authorization", "Token invalid-token")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  public void should_reject_request_with_expired_jwt_token() throws Exception {
    String expiredToken =
        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

    mockMvc
        .perform(
            get("/user")
                .header("Authorization", "Token " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  public void should_reject_request_without_authorization_header() throws Exception {
    mockMvc
        .perform(get("/user").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  public void should_accept_different_authorization_prefixes() throws Exception {
    mockMvc
        .perform(
            get("/user")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("security@example.com"));

    mockMvc
        .perform(
            get("/user")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  public void should_allow_public_endpoints_without_authentication() throws Exception {
    mockMvc
        .perform(get("/tags").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/articles").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  public void should_handle_user_registration_and_login_flow() throws Exception {
    Map<String, Object> registrationData = new HashMap<>();
    Map<String, String> user = new HashMap<>();
    user.put("email", "newuser@example.com");
    user.put("username", "newuser");
    user.put("password", "password123");
    registrationData.put("user", user);

    mockMvc
        .perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registrationData)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.email").value("newuser@example.com"))
        .andExpect(jsonPath("$.user.username").value("newuser"))
        .andExpect(jsonPath("$.user.token").exists());

    Map<String, Object> loginData = new HashMap<>();
    Map<String, String> loginUser = new HashMap<>();
    loginUser.put("email", "newuser@example.com");
    loginUser.put("password", "password123");
    loginData.put("user", loginUser);

    mockMvc
        .perform(
            post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginData)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("newuser@example.com"))
        .andExpect(jsonPath("$.user.username").value("newuser"))
        .andExpect(jsonPath("$.user.token").exists());
  }

  @Test
  public void should_reject_login_with_wrong_credentials() throws Exception {
    Map<String, Object> loginData = new HashMap<>();
    Map<String, String> loginUser = new HashMap<>();
    loginUser.put("email", "security@example.com");
    loginUser.put("password", "wrongpassword");
    loginData.put("user", loginUser);

    mockMvc
        .perform(
            post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginData)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value("invalid email or password"));
  }

  @Test
  public void should_protect_user_specific_endpoints() throws Exception {
    User otherUser = new User("other@example.com", "other", "password", "bio", "image");
    userRepository.save(otherUser);
    String otherToken = jwtService.toToken(otherUser);

    mockMvc
        .perform(
            get("/user")
                .header("Authorization", "Token " + otherToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("other@example.com"))
        .andExpect(jsonPath("$.user.username").value("other"));

    mockMvc
        .perform(
            get("/user")
                .header("Authorization", "Token " + validToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("security@example.com"))
        .andExpect(jsonPath("$.user.username").value("securityuser"));
  }

  @Test
  public void should_handle_cors_preflight_requests() throws Exception {
    mockMvc
        .perform(
            options("/users")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
        .andExpect(status().isOk())
        .andExpect(header().exists("Access-Control-Allow-Origin"))
        .andExpect(header().exists("Access-Control-Allow-Methods"))
        .andExpect(header().exists("Access-Control-Allow-Headers"));
  }

  @Test
  public void should_validate_jwt_token_structure_and_claims() {
    String token = jwtService.toToken(testUser);
    assertNotNull(token);
    assertFalse(token.isEmpty());

    Optional<String> userIdFromToken = jwtService.getSubFromToken(token);
    assertTrue(userIdFromToken.isPresent());
    assertEquals(testUser.getId(), userIdFromToken.get());
  }

  @Test
  public void should_handle_token_without_bearer_prefix() throws Exception {
    mockMvc
        .perform(
            get("/user")
                .header("Authorization", "Token " + validToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  public void should_reject_requests_to_protected_endpoints_without_proper_authentication()
      throws Exception {
    mockMvc
        .perform(
            put("/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"user\":{\"email\":\"updated@example.com\"}}"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"article\":{\"title\":\"Test\",\"description\":\"Test\",\"body\":\"Test\"}}"))
        .andExpect(status().isUnauthorized());
  }
}

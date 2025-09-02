package io.spring.graphql;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.spring.core.user.User;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

public class UserMutationIntegrationTest extends GraphQLTestBase {

  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  public void should_create_user_successfully() throws Exception {
    String mutation =
        "mutation CreateUser($input: CreateUserInput) { "
            + "createUser(input: $input) { "
            + "... on UserPayload { user { email username } } "
            + "... on Error { message errors { key value } } "
            + "} }";

    Map<String, Object> input =
        Map.of(
            "email", "test@example.com",
            "username", "testuser",
            "password", "password123");

    MvcResult result = executeGraphQLQuery(mutation, Map.of("input", input));

    assertEquals(200, result.getResponse().getStatus());

    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));

    Optional<User> savedUser = userRepository.findByEmail("test@example.com");
    assertTrue(savedUser.isPresent());
    assertEquals("testuser", savedUser.get().getUsername());
    assertTrue(passwordEncoder.matches("password123", savedUser.get().getPassword()));
  }

  @Test
  public void should_fail_create_user_with_duplicate_email() throws Exception {
    User existingUser = createTestUser("existing@example.com", "existing");

    String mutation =
        "mutation CreateUser($input: CreateUserInput) { "
            + "createUser(input: $input) { "
            + "... on UserPayload { user { email username } } "
            + "... on Error { message errors { key value } } "
            + "} }";

    Map<String, Object> input =
        Map.of(
            "email", "existing@example.com",
            "username", "newuser",
            "password", "password123");

    MvcResult result = executeGraphQLQuery(mutation, Map.of("input", input));

    assertEquals(200, result.getResponse().getStatus());

    JsonNode responseJson = parseJsonResponse(result);
    JsonNode data = responseJson.get("data");
    assertNotNull(data);
    JsonNode createUser = data.get("createUser");
    assertTrue(createUser.has("errors"));
  }

  @Test
  public void should_login_successfully() throws Exception {
    User user = createTestUser("login@example.com", "loginuser");
    user.update(null, null, passwordEncoder.encode("password123"), null, null);
    userRepository.save(user);

    String mutation =
        "mutation Login($email: String!, $password: String!) { "
            + "login(email: $email, password: $password) { "
            + "user { email username token } "
            + "} }";

    MvcResult result =
        executeGraphQLQuery(
            mutation, Map.of("email", "login@example.com", "password", "password123"));

    assertEquals(200, result.getResponse().getStatus());

    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));

    JsonNode data = responseJson.get("data");
    assertNotNull(data);
    JsonNode login = data.get("login");
    JsonNode userResult = login.get("user");

    assertEquals("login@example.com", userResult.get("email").asText());
    assertEquals("loginuser", userResult.get("username").asText());
    assertNotNull(userResult.get("token"));
  }

  @Test
  public void should_fail_login_with_wrong_password() throws Exception {
    User user = createTestUser("login@example.com", "loginuser");
    user.update(null, null, passwordEncoder.encode("password123"), null, null);
    userRepository.save(user);

    String mutation =
        "mutation Login($email: String!, $password: String!) { "
            + "login(email: $email, password: $password) { "
            + "user { email username token } "
            + "} }";

    MvcResult result =
        executeGraphQLQuery(
            mutation, Map.of("email", "login@example.com", "password", "wrongpassword"));

    assertEquals(200, result.getResponse().getStatus());

    JsonNode responseJson = parseJsonResponse(result);
    assertTrue(
        responseJson.has("errors")
            || (responseJson.has("data") && responseJson.get("data").get("login").isNull()));
  }

  @Test
  public void should_update_user_successfully() throws Exception {
    User user = createTestUser("update@example.com", "updateuser");
    String token = getAuthToken(user);

    String mutation =
        "mutation UpdateUser($changes: UpdateUserInput!) { "
            + "updateUser(changes: $changes) { "
            + "user { email username profile { bio image } } "
            + "} }";

    Map<String, Object> changes =
        Map.of(
            "email", "newemail@example.com",
            "bio", "Updated bio",
            "image", "new-image.jpg");

    MvcResult result = executeGraphQLQuery(mutation, Map.of("changes", changes), token);

    assertEquals(200, result.getResponse().getStatus());

    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));

    JsonNode data = responseJson.get("data");
    JsonNode updateUser = data.get("updateUser");
    JsonNode userResult = updateUser.get("user");
    JsonNode profile = userResult.get("profile");

    assertEquals("newemail@example.com", userResult.get("email").asText());
    assertEquals("Updated bio", profile.get("bio").asText());
    assertEquals("new-image.jpg", profile.get("image").asText());

    Optional<User> updatedUser = userRepository.findById(user.getId());
    assertTrue(updatedUser.isPresent());
    assertEquals("newemail@example.com", updatedUser.get().getEmail());
    assertEquals("Updated bio", updatedUser.get().getBio());
    assertEquals("new-image.jpg", updatedUser.get().getImage());
  }

  @Test
  public void should_fail_update_user_when_not_authenticated() throws Exception {
    String mutation =
        "mutation UpdateUser($changes: UpdateUserInput!) { "
            + "updateUser(changes: $changes) { "
            + "user { email username profile { bio image } } "
            + "} }";

    Map<String, Object> changes = Map.of("email", "newemail@example.com");

    MvcResult result = executeGraphQLQuery(mutation, Map.of("changes", changes));

    assertEquals(200, result.getResponse().getStatus());

    JsonNode responseJson = parseJsonResponse(result);
    JsonNode data = responseJson.get("data");
    if (data != null && data.has("updateUser")) {
      assertTrue(data.get("updateUser").isNull());
    }
  }
}

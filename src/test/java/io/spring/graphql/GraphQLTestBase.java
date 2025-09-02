package io.spring.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.spring.core.service.JwtService;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
public abstract class GraphQLTestBase {

  @Autowired protected MockMvc mockMvc;

  @Autowired protected UserRepository userRepository;

  @Autowired protected JwtService jwtService;

  @Autowired protected ObjectMapper objectMapper;

  protected JsonNode parseJsonResponse(MvcResult result) throws Exception {
    String responseContent = result.getResponse().getContentAsString();
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readTree(responseContent);
  }

  @BeforeEach
  public void setUp() {
    SecurityContextHolder.clearContext();
  }

  protected User createTestUser(String email, String username) {
    User user = new User(email, username, "password", "bio", "image");
    userRepository.save(user);
    return user;
  }

  protected String getAuthToken(User user) {
    return jwtService.toToken(user);
  }

  protected MvcResult executeGraphQLQuery(String query, Map<String, Object> variables, String token)
      throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("query", query);
    if (variables != null && !variables.isEmpty()) {
      requestBody.put("variables", variables);
    }

    var requestBuilder =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestBody));

    if (token != null) {
      requestBuilder.header("Authorization", "Token " + token);
    }

    return mockMvc.perform(requestBuilder).andReturn();
  }

  protected MvcResult executeGraphQLQuery(String query, Map<String, Object> variables)
      throws Exception {
    return executeGraphQLQuery(query, variables, null);
  }

  protected void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }
}

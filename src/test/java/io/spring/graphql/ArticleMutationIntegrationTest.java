package io.spring.graphql;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.favorite.ArticleFavorite;
import io.spring.core.favorite.ArticleFavoriteRepository;
import io.spring.core.user.User;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class ArticleMutationIntegrationTest extends GraphQLTestBase {

  @Autowired private ArticleRepository articleRepository;

  @Autowired private ArticleFavoriteRepository articleFavoriteRepository;

  @Test
  public void should_create_article_successfully() throws Exception {
    User user = createTestUser("author@example.com", "author");
    String token = getAuthToken(user);

    String mutation =
        "mutation CreateArticle($input: CreateArticleInput!) { "
            + "createArticle(input: $input) { "
            + "article { title description body slug tagList } "
            + "} }";

    Map<String, Object> input =
        Map.of(
            "title", "Test Article",
            "description", "Test Description",
            "body", "Test Body",
            "tagList", Arrays.asList("java", "spring"));

    MvcResult result = executeGraphQLQuery(mutation, Map.of("input", input), token);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));
    assertNotNull(responseJson.get("data"));

    JsonNode data = responseJson.get("data");
    JsonNode createArticle = data.get("createArticle");
    JsonNode article = createArticle.get("article");

    assertEquals("Test Article", article.get("title").asText());
    assertEquals("Test Description", article.get("description").asText());
    assertEquals("Test Body", article.get("body").asText());
    assertNotNull(article.get("slug"));

    String slug = article.get("slug").asText();
    Optional<Article> savedArticle = articleRepository.findBySlug(slug);
    assertTrue(savedArticle.isPresent());
    assertEquals(user.getId(), savedArticle.get().getUserId());
  }

  @Test
  public void should_fail_create_article_when_not_authenticated() throws Exception {
    String mutation =
        "mutation CreateArticle($input: CreateArticleInput!) { "
            + "createArticle(input: $input) { "
            + "article { title description body slug } "
            + "} }";

    Map<String, Object> input =
        Map.of(
            "title", "Test Article",
            "description", "Test Description",
            "body", "Test Body");

    MvcResult result = executeGraphQLQuery(mutation, Map.of("input", input));

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertTrue(
        responseJson.has("errors")
            || (responseJson.has("data")
                && responseJson.get("data").get("createArticle").isNull()));
  }

  @Test
  public void should_update_article_successfully() throws Exception {
    User user = createTestUser("author@example.com", "author");
    Article article =
        new Article(
            "Original Title",
            "Original Description",
            "Original Body",
            Arrays.asList(),
            user.getId());
    articleRepository.save(article);
    String token = getAuthToken(user);

    String mutation =
        "mutation UpdateArticle($slug: String!, $changes: UpdateArticleInput!) { "
            + "updateArticle(slug: $slug, changes: $changes) { "
            + "article { title description body } "
            + "} }";

    Map<String, Object> changes =
        Map.of(
            "title", "Updated Title",
            "description", "Updated Description",
            "body", "Updated Body");

    MvcResult result =
        executeGraphQLQuery(mutation, Map.of("slug", article.getSlug(), "changes", changes), token);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));
    assertNotNull(responseJson.get("data"));

    JsonNode data = responseJson.get("data");
    JsonNode updateArticle = data.get("updateArticle");
    JsonNode articleResult = updateArticle.get("article");

    assertEquals("Updated Title", articleResult.get("title").asText());
    assertEquals("Updated Description", articleResult.get("description").asText());
    assertEquals("Updated Body", articleResult.get("body").asText());

    Optional<Article> updatedArticle = articleRepository.findById(article.getId());
    assertTrue(updatedArticle.isPresent(), "Updated article should be found in database");
    assertEquals("Updated Title", updatedArticle.get().getTitle());
    assertEquals("Updated Description", updatedArticle.get().getDescription());
    assertEquals("Updated Body", updatedArticle.get().getBody());
  }

  @Test
  public void should_fail_update_article_when_not_authorized() throws Exception {
    User author = createTestUser("author@example.com", "author");
    User otherUser = createTestUser("other@example.com", "other");
    Article article =
        new Article(
            "Original Title",
            "Original Description",
            "Original Body",
            Arrays.asList(),
            author.getId());
    articleRepository.save(article);
    String otherToken = getAuthToken(otherUser);

    String mutation =
        "mutation UpdateArticle($slug: String!, $changes: UpdateArticleInput!) { "
            + "updateArticle(slug: $slug, changes: $changes) { "
            + "article { title description body } "
            + "} }";

    Map<String, Object> changes = Map.of("title", "Updated Title");

    MvcResult result =
        executeGraphQLQuery(
            mutation, Map.of("slug", article.getSlug(), "changes", changes), otherToken);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertTrue(responseJson.has("errors"));
  }

  @Test
  public void should_favorite_article_successfully() throws Exception {
    User author = createTestUser("author@example.com", "author");
    User user = createTestUser("user@example.com", "user");
    Article article =
        new Article(
            "Test Article", "Test Description", "Test Body", Arrays.asList(), author.getId());
    articleRepository.save(article);
    String userToken = getAuthToken(user);

    String mutation =
        "mutation FavoriteArticle($slug: String!) { "
            + "favoriteArticle(slug: $slug) { "
            + "article { slug favorited favoritesCount } "
            + "} }";

    MvcResult result = executeGraphQLQuery(mutation, Map.of("slug", article.getSlug()), userToken);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));
    assertNotNull(responseJson.get("data"));

    Optional<ArticleFavorite> favorite =
        articleFavoriteRepository.find(article.getId(), user.getId());
    assertTrue(favorite.isPresent());
  }

  @Test
  public void should_unfavorite_article_successfully() throws Exception {
    User author = createTestUser("author@example.com", "author");
    User user = createTestUser("user@example.com", "user");
    Article article =
        new Article(
            "Test Article", "Test Description", "Test Body", Arrays.asList(), author.getId());
    articleRepository.save(article);

    ArticleFavorite favorite = new ArticleFavorite(article.getId(), user.getId());
    articleFavoriteRepository.save(favorite);
    String userToken = getAuthToken(user);

    String mutation =
        "mutation UnfavoriteArticle($slug: String!) { "
            + "unfavoriteArticle(slug: $slug) { "
            + "article { slug favorited favoritesCount } "
            + "} }";

    MvcResult result = executeGraphQLQuery(mutation, Map.of("slug", article.getSlug()), userToken);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));
    assertNotNull(responseJson.get("data"));

    Optional<ArticleFavorite> removedFavorite =
        articleFavoriteRepository.find(article.getId(), user.getId());
    assertFalse(removedFavorite.isPresent());
  }

  @Test
  public void should_delete_article_successfully() throws Exception {
    User user = createTestUser("author@example.com", "author");
    Article article =
        new Article("Test Article", "Test Description", "Test Body", Arrays.asList(), user.getId());
    articleRepository.save(article);
    String token = getAuthToken(user);

    String mutation =
        "mutation DeleteArticle($slug: String!) { "
            + "deleteArticle(slug: $slug) { success } "
            + "}";

    MvcResult result = executeGraphQLQuery(mutation, Map.of("slug", article.getSlug()), token);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));
    assertNotNull(responseJson.get("data"));

    JsonNode data = responseJson.get("data");
    JsonNode deleteArticle = data.get("deleteArticle");
    assertTrue(deleteArticle.get("success").asBoolean());

    Optional<Article> deletedArticle = articleRepository.findBySlug(article.getSlug());
    assertFalse(deletedArticle.isPresent());
  }

  @Test
  public void should_fail_delete_article_when_not_authorized() throws Exception {
    User author = createTestUser("author@example.com", "author");
    User otherUser = createTestUser("other@example.com", "other");
    Article article =
        new Article(
            "Test Article", "Test Description", "Test Body", Arrays.asList(), author.getId());
    articleRepository.save(article);
    String otherToken = getAuthToken(otherUser);

    String mutation =
        "mutation DeleteArticle($slug: String!) { "
            + "deleteArticle(slug: $slug) { success } "
            + "}";

    MvcResult result = executeGraphQLQuery(mutation, Map.of("slug", article.getSlug()), otherToken);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertTrue(responseJson.has("errors"));

    Optional<Article> article_still_exists = articleRepository.findBySlug(article.getSlug());
    assertTrue(article_still_exists.isPresent());
  }
}

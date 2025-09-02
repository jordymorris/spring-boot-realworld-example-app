package io.spring.graphql;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.user.User;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class CommentMutationIntegrationTest extends GraphQLTestBase {

  @Autowired private ArticleRepository articleRepository;

  @Autowired private CommentRepository commentRepository;

  @Test
  public void should_add_comment_successfully() throws Exception {
    User author = createTestUser("author@example.com", "author");
    User commenter = createTestUser("commenter@example.com", "commenter");
    Article article =
        new Article(
            "Test Article", "Test Description", "Test Body", Arrays.asList(), author.getId());
    articleRepository.save(article);
    String commenterToken = getAuthToken(commenter);

    String mutation =
        "mutation AddComment($slug: String!, $body: String!) { "
            + "addComment(slug: $slug, body: $body) { "
            + "comment { id body createdAt updatedAt } "
            + "} }";

    MvcResult result =
        executeGraphQLQuery(
            mutation,
            Map.of("slug", article.getSlug(), "body", "This is a test comment"),
            commenterToken);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));

    JsonNode data = responseJson.get("data");
    JsonNode addComment = data.get("addComment");
    JsonNode comment = addComment.get("comment");

    assertNotNull(comment.get("id"));
    assertEquals("This is a test comment", comment.get("body").asText());
    assertNotNull(comment.get("createdAt"));
    assertNotNull(comment.get("updatedAt"));

    String commentId = comment.get("id").asText();
    Optional<Comment> savedComment = commentRepository.findById(article.getId(), commentId);
    assertTrue(savedComment.isPresent());
    assertEquals("This is a test comment", savedComment.get().getBody());
    assertEquals(commenter.getId(), savedComment.get().getUserId());
  }

  @Test
  public void should_fail_add_comment_when_not_authenticated() throws Exception {
    User author = createTestUser("author@example.com", "author");
    Article article =
        new Article(
            "Test Article", "Test Description", "Test Body", Arrays.asList(), author.getId());
    articleRepository.save(article);

    String mutation =
        "mutation AddComment($slug: String!, $body: String!) { "
            + "addComment(slug: $slug, body: $body) { "
            + "comment { id body } "
            + "} }";

    MvcResult result =
        executeGraphQLQuery(
            mutation, Map.of("slug", article.getSlug(), "body", "This is a test comment"));

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertTrue(
        responseJson.has("errors")
            || (responseJson.has("data") && responseJson.get("data").get("addComment").isNull()));
  }

  @Test
  public void should_fail_add_comment_when_article_not_found() throws Exception {
    User commenter = createTestUser("commenter@example.com", "commenter");
    String commenterToken = getAuthToken(commenter);

    String mutation =
        "mutation AddComment($slug: String!, $body: String!) { "
            + "addComment(slug: $slug, body: $body) { "
            + "comment { id body } "
            + "} }";

    MvcResult result =
        executeGraphQLQuery(
            mutation,
            Map.of("slug", "non-existent-slug", "body", "This is a test comment"),
            commenterToken);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertTrue(responseJson.has("errors"));
  }

  @Test
  public void should_delete_comment_successfully() throws Exception {
    User author = createTestUser("author@example.com", "author");
    User commenter = createTestUser("commenter@example.com", "commenter");
    Article article =
        new Article(
            "Test Article", "Test Description", "Test Body", Arrays.asList(), author.getId());
    articleRepository.save(article);

    Comment comment = new Comment("Test comment", commenter.getId(), article.getId());
    commentRepository.save(comment);
    String commenterToken = getAuthToken(commenter);

    String mutation =
        "mutation DeleteComment($slug: String!, $id: ID!) { "
            + "deleteComment(slug: $slug, id: $id) { success } "
            + "}";

    MvcResult result =
        executeGraphQLQuery(
            mutation, Map.of("slug", article.getSlug(), "id", comment.getId()), commenterToken);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));

    JsonNode data = responseJson.get("data");
    JsonNode deleteComment = data.get("deleteComment");
    assertTrue(deleteComment.get("success").asBoolean());

    Optional<Comment> deletedComment = commentRepository.findById(article.getId(), comment.getId());
    assertFalse(deletedComment.isPresent());
  }

  @Test
  public void should_fail_delete_comment_when_not_authorized() throws Exception {
    User author = createTestUser("author@example.com", "author");
    User commenter = createTestUser("commenter@example.com", "commenter");
    User otherUser = createTestUser("other@example.com", "other");
    Article article =
        new Article(
            "Test Article", "Test Description", "Test Body", Arrays.asList(), author.getId());
    articleRepository.save(article);

    Comment comment = new Comment("Test comment", commenter.getId(), article.getId());
    commentRepository.save(comment);
    String otherUserToken = getAuthToken(otherUser);

    String mutation =
        "mutation DeleteComment($slug: String!, $id: ID!) { "
            + "deleteComment(slug: $slug, id: $id) { success } "
            + "}";

    MvcResult result =
        executeGraphQLQuery(
            mutation, Map.of("slug", article.getSlug(), "id", comment.getId()), otherUserToken);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertTrue(responseJson.has("errors"));

    Optional<Comment> commentStillExists =
        commentRepository.findById(article.getId(), comment.getId());
    assertTrue(commentStillExists.isPresent());
  }

  @Test
  public void should_fail_delete_comment_when_comment_not_found() throws Exception {
    User author = createTestUser("author@example.com", "author");
    User commenter = createTestUser("commenter@example.com", "commenter");
    Article article =
        new Article(
            "Test Article", "Test Description", "Test Body", Arrays.asList(), author.getId());
    articleRepository.save(article);
    String commenterToken = getAuthToken(commenter);

    String mutation =
        "mutation DeleteComment($slug: String!, $id: ID!) { "
            + "deleteComment(slug: $slug, id: $id) { success } "
            + "}";

    MvcResult result =
        executeGraphQLQuery(
            mutation,
            Map.of("slug", article.getSlug(), "id", "non-existent-comment-id"),
            commenterToken);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertTrue(responseJson.has("errors"));
  }
}

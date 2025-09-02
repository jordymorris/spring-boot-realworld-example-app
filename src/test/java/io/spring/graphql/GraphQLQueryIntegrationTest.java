package io.spring.graphql;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.favorite.ArticleFavoriteRepository;
import io.spring.core.user.FollowRelation;
import io.spring.core.user.User;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class GraphQLQueryIntegrationTest extends GraphQLTestBase {

  @Autowired private ArticleRepository articleRepository;

  @Autowired private CommentRepository commentRepository;

  @Autowired private ArticleFavoriteRepository articleFavoriteRepository;

  @Test
  public void should_query_article_with_author_profile() throws Exception {
    User author = createTestUser("author@example.com", "author");
    Article article =
        new Article(
            "Test Article", "Test Description", "Test Body", Arrays.asList("java"), author.getId());
    articleRepository.save(article);

    String query =
        "query GetArticle($slug: String!) { "
            + "article(slug: $slug) { "
            + "title description body slug tagList "
            + "author { username bio image following } "
            + "} }";

    MvcResult result = executeGraphQLQuery(query, Map.of("slug", article.getSlug()));

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));
    assertNotNull(responseJson.get("data"));

    JsonNode data = responseJson.get("data");
    JsonNode articleData = data.get("article");

    assertEquals("Test Article", articleData.get("title").asText());
    assertEquals("Test Description", articleData.get("description").asText());
    assertEquals("Test Body", articleData.get("body").asText());
    assertEquals(article.getSlug(), articleData.get("slug").asText());

    JsonNode tagListNode = articleData.get("tagList");
    assertTrue(tagListNode.isArray());
    boolean foundJava = false;
    for (JsonNode tagNode : tagListNode) {
      if ("java".equals(tagNode.asText())) {
        foundJava = true;
        break;
      }
    }
    assertTrue(foundJava);

    JsonNode authorData = articleData.get("author");
    assertEquals("author", authorData.get("username").asText());
    assertEquals("bio", authorData.get("bio").asText());
    assertEquals("image", authorData.get("image").asText());
    assertFalse(authorData.get("following").asBoolean());
  }

  @Test
  public void should_query_user_profile_with_following_status() throws Exception {
    User user = createTestUser("user@example.com", "user");
    User targetUser = createTestUser("target@example.com", "target");

    FollowRelation followRelation = new FollowRelation(user.getId(), targetUser.getId());
    userRepository.saveRelation(followRelation);

    String query =
        "query GetProfile($username: String!) { "
            + "profile(username: $username) { "
            + "profile { username bio image following } "
            + "} }";

    String userToken = getAuthToken(user);
    MvcResult result = executeGraphQLQuery(query, Map.of("username", "target"), userToken);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));
    assertNotNull(responseJson.get("data"));

    JsonNode data = responseJson.get("data");
    JsonNode profilePayload = data.get("profile");
    JsonNode profile = profilePayload.get("profile");

    assertEquals("target", profile.get("username").asText());
    assertEquals("bio", profile.get("bio").asText());
    assertEquals("image", profile.get("image").asText());
    assertTrue(profile.get("following").asBoolean());
  }

  @Test
  public void should_query_articles_with_pagination() throws Exception {
    User author = createTestUser("author@example.com", "author");

    for (int i = 1; i <= 5; i++) {
      Article article =
          new Article(
              "Article " + i,
              "Description " + i,
              "Body " + i,
              Arrays.asList("tag" + i),
              author.getId());
      articleRepository.save(article);
    }

    String query =
        "query GetArticles($first: Int) { "
            + "articles(first: $first) { "
            + "edges { "
            + "node { title description slug } "
            + "cursor "
            + "} "
            + "pageInfo { hasNextPage hasPreviousPage startCursor endCursor } "
            + "} }";

    MvcResult result = executeGraphQLQuery(query, Map.of("first", 3));

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));
    assertNotNull(responseJson.get("data"));

    JsonNode data = responseJson.get("data");
    JsonNode articles = data.get("articles");
    JsonNode edges = articles.get("edges");

    assertTrue(edges.isArray());
    assertEquals(3, edges.size());

    JsonNode pageInfo = articles.get("pageInfo");
    assertNotNull(pageInfo.get("startCursor"));
    assertNotNull(pageInfo.get("endCursor"));
  }

  @Test
  public void should_query_me_when_authenticated() throws Exception {
    User user = createTestUser("me@example.com", "me");

    String query =
        "query GetMe { "
            + "me { "
            + "email username "
            + "profile { username bio image following } "
            + "} }";

    String userToken = getAuthToken(user);
    MvcResult result = executeGraphQLQuery(query, Map.of(), userToken);

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));
    assertNotNull(responseJson.get("data"));

    JsonNode data = responseJson.get("data");
    JsonNode me = data.get("me");

    assertEquals("me@example.com", me.get("email").asText());
    assertEquals("me", me.get("username").asText());

    JsonNode profile = me.get("profile");
    assertEquals("me", profile.get("username").asText());
    assertEquals("bio", profile.get("bio").asText());
    assertEquals("image", profile.get("image").asText());
  }

  @Test
  public void should_query_article_with_comments() throws Exception {
    User author = createTestUser("author@example.com", "author");
    User commenter = createTestUser("commenter@example.com", "commenter");
    Article article =
        new Article(
            "Test Article", "Test Description", "Test Body", Arrays.asList(), author.getId());
    articleRepository.save(article);

    Comment comment1 = new Comment("First comment", commenter.getId(), article.getId());
    Comment comment2 = new Comment("Second comment", commenter.getId(), article.getId());
    commentRepository.save(comment1);
    commentRepository.save(comment2);

    String query =
        "query GetArticleWithComments($slug: String!) { "
            + "article(slug: $slug) { "
            + "title "
            + "comments(first: 10) { "
            + "edges { "
            + "node { "
            + "id body "
            + "author { username bio image } "
            + "} "
            + "} "
            + "} "
            + "} }";

    MvcResult result = executeGraphQLQuery(query, Map.of("slug", article.getSlug()));

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));
    assertNotNull(responseJson.get("data"));

    JsonNode data = responseJson.get("data");
    JsonNode articleData = data.get("article");
    JsonNode comments = articleData.get("comments");
    JsonNode edges = comments.get("edges");

    assertTrue(edges.isArray());
    assertEquals(2, edges.size());

    JsonNode firstCommentNode = edges.get(0).get("node");
    assertNotNull(firstCommentNode.get("id"));
    assertNotNull(firstCommentNode.get("body"));

    JsonNode commentAuthor = firstCommentNode.get("author");
    assertEquals("commenter", commentAuthor.get("username").asText());
  }

  @Test
  public void should_query_tags() throws Exception {
    User author = createTestUser("author@example.com", "author");
    Article article1 =
        new Article(
            "Article 1",
            "Description 1",
            "Body 1",
            Arrays.asList("java", "spring"),
            author.getId());
    Article article2 =
        new Article(
            "Article 2",
            "Description 2",
            "Body 2",
            Arrays.asList("kotlin", "spring"),
            author.getId());
    articleRepository.save(article1);
    articleRepository.save(article2);

    String query = "query GetTags { tags }";

    MvcResult result = executeGraphQLQuery(query, Map.of());

    assertEquals(200, result.getResponse().getStatus());
    JsonNode responseJson = parseJsonResponse(result);
    assertFalse(responseJson.has("errors"));
    assertNotNull(responseJson.get("data"));

    JsonNode data = responseJson.get("data");
    JsonNode tagsNode = data.get("tags");
    List<String> tags = new ArrayList<>();
    if (tagsNode.isArray()) {
      for (JsonNode tagNode : tagsNode) {
        tags.add(tagNode.asText());
      }
    }

    assertTrue(tags.contains("java"));
    assertTrue(tags.contains("spring"));
    assertTrue(tags.contains("kotlin"));
  }
}

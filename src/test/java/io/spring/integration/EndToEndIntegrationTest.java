package io.spring.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.spring.application.ArticleQueryService;
import io.spring.application.CommentQueryService;
import io.spring.application.Page;
import io.spring.application.ProfileQueryService;
import io.spring.application.UserQueryService;
import io.spring.application.article.ArticleCommandService;
import io.spring.application.article.NewArticleParam;
import io.spring.application.data.ArticleData;
import io.spring.application.data.ArticleDataList;
import io.spring.application.data.CommentData;
import io.spring.application.data.ProfileData;
import io.spring.application.data.UserData;
import io.spring.application.user.RegisterParam;
import io.spring.application.user.UserService;
import io.spring.core.article.Article;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.favorite.ArticleFavorite;
import io.spring.core.favorite.ArticleFavoriteRepository;
import io.spring.core.user.FollowRelation;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
public class EndToEndIntegrationTest {

  @Autowired private UserService userService;

  @Autowired private UserQueryService userQueryService;

  @Autowired private ArticleCommandService articleCommandService;

  @Autowired private ArticleQueryService articleQueryService;

  @Autowired private CommentQueryService commentQueryService;

  @Autowired private ProfileQueryService profileQueryService;

  @Autowired private UserRepository userRepository;

  @Autowired private CommentRepository commentRepository;

  @Autowired private ArticleFavoriteRepository articleFavoriteRepository;

  @Test
  public void should_complete_user_registration_to_article_creation_to_comment_flow() {
    RegisterParam registerParam = new RegisterParam("author@example.com", "author", "password");
    User author = userService.createUser(registerParam);
    assertNotNull(author.getId());

    UserData authorData = userQueryService.findById(author.getId()).get();
    assertEquals("author@example.com", authorData.getEmail());
    assertEquals("author", authorData.getUsername());

    NewArticleParam articleParam =
        NewArticleParam.builder()
            .title("Integration Test Article")
            .description("Testing end-to-end flow")
            .body("This article tests the complete integration")
            .tagList(Arrays.asList("integration", "test"))
            .build();

    Article article = articleCommandService.createArticle(articleParam, author);
    assertNotNull(article.getId());
    assertNotNull(article.getSlug());

    Optional<ArticleData> articleData = articleQueryService.findBySlug(article.getSlug(), author);
    assertTrue(articleData.isPresent());
    assertEquals("Integration Test Article", articleData.get().getTitle());
    assertEquals("Testing end-to-end flow", articleData.get().getDescription());
    assertEquals("This article tests the complete integration", articleData.get().getBody());
    assertTrue(articleData.get().getTagList().contains("integration"));
    assertTrue(articleData.get().getTagList().contains("test"));

    RegisterParam commenterParam =
        new RegisterParam("commenter@example.com", "commenter", "password");
    User commenter = userService.createUser(commenterParam);

    Comment comment = new Comment("Great article!", commenter.getId(), article.getId());
    commentRepository.save(comment);

    Optional<CommentData> commentData = commentQueryService.findById(comment.getId(), commenter);
    assertTrue(commentData.isPresent());
    assertEquals("Great article!", commentData.get().getBody());
    assertEquals("commenter", commentData.get().getProfileData().getUsername());

    List<CommentData> articleComments =
        commentQueryService.findByArticleId(article.getId(), commenter);
    assertEquals(1, articleComments.size());
    assertEquals("Great article!", articleComments.get(0).getBody());
  }

  @Test
  public void should_complete_user_following_to_feed_generation_flow() {
    RegisterParam followerParam = new RegisterParam("follower@example.com", "follower", "password");
    User follower = userService.createUser(followerParam);

    RegisterParam authorParam = new RegisterParam("author@example.com", "author", "password");
    User author = userService.createUser(authorParam);

    FollowRelation followRelation = new FollowRelation(follower.getId(), author.getId());
    userRepository.saveRelation(followRelation);

    Optional<ProfileData> authorProfile = profileQueryService.findByUsername("author", follower);
    assertTrue(authorProfile.isPresent());
    assertTrue(authorProfile.get().isFollowing());
    assertEquals("author", authorProfile.get().getUsername());

    NewArticleParam articleParam =
        NewArticleParam.builder()
            .title("Article by Followed Author")
            .description("This should appear in feed")
            .body("Content from followed author")
            .tagList(Arrays.asList("feed"))
            .build();

    Article article = articleCommandService.createArticle(articleParam, author);

    Page page = new Page(0, 10);
    ArticleDataList feedResult = articleQueryService.findUserFeed(follower, page);
    List<ArticleData> feedArticles = feedResult.getArticleDatas();
    assertEquals(1, feedArticles.size());
    assertEquals("Article by Followed Author", feedArticles.get(0).getTitle());
    assertEquals("author", feedArticles.get(0).getProfileData().getUsername());
    assertTrue(feedArticles.get(0).getProfileData().isFollowing());
  }

  @Test
  public void should_complete_article_favoriting_to_profile_favorites_flow() {
    RegisterParam userParam = new RegisterParam("user@example.com", "user", "password");
    User user = userService.createUser(userParam);

    RegisterParam authorParam = new RegisterParam("author@example.com", "author", "password");
    User author = userService.createUser(authorParam);

    NewArticleParam articleParam =
        NewArticleParam.builder()
            .title("Favorite Article")
            .description("This will be favorited")
            .body("Great content to favorite")
            .tagList(Arrays.asList("favorite"))
            .build();

    Article article = articleCommandService.createArticle(articleParam, author);

    ArticleFavorite favorite = new ArticleFavorite(article.getId(), user.getId());
    articleFavoriteRepository.save(favorite);

    Optional<ArticleData> articleData = articleQueryService.findBySlug(article.getSlug(), user);
    assertTrue(articleData.isPresent());
    assertTrue(articleData.get().isFavorited());
    assertEquals(1, articleData.get().getFavoritesCount());

    Page page2 = new Page(0, 10);
    ArticleDataList favoritesResult =
        articleQueryService.findRecentArticles(null, null, "user", page2, user);
    List<ArticleData> userFavorites = favoritesResult.getArticleDatas();
    assertEquals(1, userFavorites.size());
    assertEquals("Favorite Article", userFavorites.get(0).getTitle());
    assertTrue(userFavorites.get(0).isFavorited());

    Optional<ProfileData> userProfile = profileQueryService.findByUsername("user", author);
    assertTrue(userProfile.isPresent());
    assertEquals("user", userProfile.get().getUsername());
  }

  @Test
  public void should_maintain_data_consistency_across_all_layers() {
    RegisterParam userParam =
        new RegisterParam("consistency@example.com", "consistency", "password");
    User user = userService.createUser(userParam);

    NewArticleParam articleParam =
        NewArticleParam.builder()
            .title("Consistency Test")
            .description("Testing data consistency")
            .body("Ensuring all layers work together")
            .tagList(Arrays.asList("consistency", "integration"))
            .build();

    Article article = articleCommandService.createArticle(articleParam, user);

    Comment comment = new Comment("Consistent comment", user.getId(), article.getId());
    commentRepository.save(comment);

    ArticleFavorite favorite = new ArticleFavorite(article.getId(), user.getId());
    articleFavoriteRepository.save(favorite);

    Optional<ArticleData> articleFromQuery =
        articleQueryService.findBySlug(article.getSlug(), user);
    assertTrue(articleFromQuery.isPresent());
    assertEquals("Consistency Test", articleFromQuery.get().getTitle());
    assertTrue(articleFromQuery.get().isFavorited());
    assertEquals(1, articleFromQuery.get().getFavoritesCount());
    assertEquals("consistency", articleFromQuery.get().getProfileData().getUsername());

    List<CommentData> commentsFromQuery =
        commentQueryService.findByArticleId(article.getId(), user);
    assertEquals(1, commentsFromQuery.size());
    assertEquals("Consistent comment", commentsFromQuery.get(0).getBody());
    assertEquals("consistency", commentsFromQuery.get(0).getProfileData().getUsername());

    Optional<UserData> userFromQuery = userQueryService.findById(user.getId());
    assertTrue(userFromQuery.isPresent());
    assertEquals("consistency@example.com", userFromQuery.get().getEmail());
    assertEquals("consistency", userFromQuery.get().getUsername());

    Optional<ProfileData> profileFromQuery =
        profileQueryService.findByUsername("consistency", user);
    assertTrue(profileFromQuery.isPresent());
    assertEquals("consistency", profileFromQuery.get().getUsername());
    assertFalse(profileFromQuery.get().isFollowing());
  }
}

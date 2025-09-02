package io.spring.infrastructure.favorite;

import static org.junit.jupiter.api.Assertions.*;

import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.favorite.ArticleFavorite;
import io.spring.core.favorite.ArticleFavoriteRepository;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import io.spring.infrastructure.DbTestBase;
import io.spring.infrastructure.repository.MyBatisArticleFavoriteRepository;
import io.spring.infrastructure.repository.MyBatisArticleRepository;
import io.spring.infrastructure.repository.MyBatisUserRepository;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({
  MyBatisArticleFavoriteRepository.class,
  MyBatisArticleRepository.class,
  MyBatisUserRepository.class
})
public class MyBatisArticleFavoriteRepositoryIntegrationTest extends DbTestBase {

  @Autowired private ArticleFavoriteRepository articleFavoriteRepository;

  @Autowired private ArticleRepository articleRepository;

  @Autowired private UserRepository userRepository;

  private User user;
  private Article article;

  @BeforeEach
  public void setUp() {
    user = new User("test@example.com", "testuser", "password", "bio", "image");
    userRepository.save(user);

    article =
        new Article("Test Article", "Description", "Body", Arrays.asList("tag1"), user.getId());
    articleRepository.save(article);
  }

  @Test
  public void should_save_and_find_article_favorite() {
    ArticleFavorite favorite = new ArticleFavorite(article.getId(), user.getId());
    articleFavoriteRepository.save(favorite);

    Optional<ArticleFavorite> found = articleFavoriteRepository.find(article.getId(), user.getId());
    assertTrue(found.isPresent());
    assertEquals(article.getId(), found.get().getArticleId());
    assertEquals(user.getId(), found.get().getUserId());
  }

  @Test
  public void should_remove_article_favorite() {
    ArticleFavorite favorite = new ArticleFavorite(article.getId(), user.getId());
    articleFavoriteRepository.save(favorite);

    Optional<ArticleFavorite> found = articleFavoriteRepository.find(article.getId(), user.getId());
    assertTrue(found.isPresent());

    articleFavoriteRepository.remove(favorite);

    Optional<ArticleFavorite> removed =
        articleFavoriteRepository.find(article.getId(), user.getId());
    assertFalse(removed.isPresent());
  }

  @Test
  public void should_handle_multiple_users_favoriting_same_article() {
    User user2 = new User("user2@example.com", "user2", "password", "bio", "image");
    User user3 = new User("user3@example.com", "user3", "password", "bio", "image");
    userRepository.save(user2);
    userRepository.save(user3);

    ArticleFavorite favorite1 = new ArticleFavorite(article.getId(), user.getId());
    ArticleFavorite favorite2 = new ArticleFavorite(article.getId(), user2.getId());
    ArticleFavorite favorite3 = new ArticleFavorite(article.getId(), user3.getId());

    articleFavoriteRepository.save(favorite1);
    articleFavoriteRepository.save(favorite2);
    articleFavoriteRepository.save(favorite3);

    Optional<ArticleFavorite> found1 =
        articleFavoriteRepository.find(article.getId(), user.getId());
    Optional<ArticleFavorite> found2 =
        articleFavoriteRepository.find(article.getId(), user2.getId());
    Optional<ArticleFavorite> found3 =
        articleFavoriteRepository.find(article.getId(), user3.getId());

    assertTrue(found1.isPresent());
    assertTrue(found2.isPresent());
    assertTrue(found3.isPresent());
  }

  @Test
  public void should_handle_user_favoriting_multiple_articles() {
    Article article2 =
        new Article("Second Article", "Description 2", "Body 2", Arrays.asList(), user.getId());
    Article article3 =
        new Article("Third Article", "Description 3", "Body 3", Arrays.asList(), user.getId());
    articleRepository.save(article2);
    articleRepository.save(article3);

    ArticleFavorite favorite1 = new ArticleFavorite(article.getId(), user.getId());
    ArticleFavorite favorite2 = new ArticleFavorite(article2.getId(), user.getId());
    ArticleFavorite favorite3 = new ArticleFavorite(article3.getId(), user.getId());

    articleFavoriteRepository.save(favorite1);
    articleFavoriteRepository.save(favorite2);
    articleFavoriteRepository.save(favorite3);

    Optional<ArticleFavorite> found1 =
        articleFavoriteRepository.find(article.getId(), user.getId());
    Optional<ArticleFavorite> found2 =
        articleFavoriteRepository.find(article2.getId(), user.getId());
    Optional<ArticleFavorite> found3 =
        articleFavoriteRepository.find(article3.getId(), user.getId());

    assertTrue(found1.isPresent());
    assertTrue(found2.isPresent());
    assertTrue(found3.isPresent());
  }

  @Test
  public void should_not_find_non_existent_favorite() {
    User user2 = new User("user2@example.com", "user2", "password", "bio", "image");
    userRepository.save(user2);

    Optional<ArticleFavorite> notFound =
        articleFavoriteRepository.find(article.getId(), user2.getId());
    assertFalse(notFound.isPresent());
  }

  @Test
  public void should_handle_duplicate_favorite_attempts() {
    ArticleFavorite favorite = new ArticleFavorite(article.getId(), user.getId());
    articleFavoriteRepository.save(favorite);

    Optional<ArticleFavorite> found = articleFavoriteRepository.find(article.getId(), user.getId());
    assertTrue(found.isPresent());

    ArticleFavorite duplicateFavorite = new ArticleFavorite(article.getId(), user.getId());
    articleFavoriteRepository.save(duplicateFavorite);

    Optional<ArticleFavorite> stillFound =
        articleFavoriteRepository.find(article.getId(), user.getId());
    assertTrue(stillFound.isPresent());
  }
}

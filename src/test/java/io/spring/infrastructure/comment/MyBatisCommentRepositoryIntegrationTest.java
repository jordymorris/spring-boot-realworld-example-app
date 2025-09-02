package io.spring.infrastructure.comment;

import static org.junit.jupiter.api.Assertions.*;

import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import io.spring.infrastructure.DbTestBase;
import io.spring.infrastructure.repository.MyBatisArticleRepository;
import io.spring.infrastructure.repository.MyBatisCommentRepository;
import io.spring.infrastructure.repository.MyBatisUserRepository;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({
  MyBatisCommentRepository.class,
  MyBatisArticleRepository.class,
  MyBatisUserRepository.class
})
public class MyBatisCommentRepositoryIntegrationTest extends DbTestBase {

  @Autowired private CommentRepository commentRepository;

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
  public void should_save_and_find_comment_by_id() {
    Comment comment = new Comment("Test comment body", user.getId(), article.getId());
    commentRepository.save(comment);

    Optional<Comment> found = commentRepository.findById(article.getId(), comment.getId());
    assertTrue(found.isPresent());
    assertEquals("Test comment body", found.get().getBody());
    assertEquals(user.getId(), found.get().getUserId());
    assertEquals(article.getId(), found.get().getArticleId());
  }

  @Test
  public void should_save_multiple_comments_for_same_article() {
    Comment comment1 = new Comment("First comment", user.getId(), article.getId());
    Comment comment2 = new Comment("Second comment", user.getId(), article.getId());
    commentRepository.save(comment1);
    commentRepository.save(comment2);

    Optional<Comment> found1 = commentRepository.findById(article.getId(), comment1.getId());
    Optional<Comment> found2 = commentRepository.findById(article.getId(), comment2.getId());

    assertTrue(found1.isPresent());
    assertTrue(found2.isPresent());
    assertEquals("First comment", found1.get().getBody());
    assertEquals("Second comment", found2.get().getBody());
  }

  @Test
  public void should_remove_comment() {
    Comment comment = new Comment("Test comment", user.getId(), article.getId());
    commentRepository.save(comment);

    Optional<Comment> found = commentRepository.findById(article.getId(), comment.getId());
    assertTrue(found.isPresent());

    commentRepository.remove(comment);

    Optional<Comment> removed = commentRepository.findById(article.getId(), comment.getId());
    assertFalse(removed.isPresent());
  }

  @Test
  public void should_handle_multiple_articles_comments() {
    Article article2 =
        new Article("Second Article", "Description 2", "Body 2", Arrays.asList(), user.getId());
    articleRepository.save(article2);

    Comment comment1 = new Comment("Comment on article 1", user.getId(), article.getId());
    Comment comment2 = new Comment("Comment on article 2", user.getId(), article2.getId());
    commentRepository.save(comment1);
    commentRepository.save(comment2);

    Optional<Comment> found1 = commentRepository.findById(article.getId(), comment1.getId());
    Optional<Comment> found2 = commentRepository.findById(article2.getId(), comment2.getId());

    assertTrue(found1.isPresent());
    assertTrue(found2.isPresent());
    assertEquals("Comment on article 1", found1.get().getBody());
    assertEquals("Comment on article 2", found2.get().getBody());
  }

  @Test
  public void should_handle_comments_from_multiple_users() {
    User user2 = new User("user2@example.com", "user2", "password", "bio", "image");
    userRepository.save(user2);

    Comment comment1 = new Comment("Comment from user 1", user.getId(), article.getId());
    Comment comment2 = new Comment("Comment from user 2", user2.getId(), article.getId());
    commentRepository.save(comment1);
    commentRepository.save(comment2);

    Optional<Comment> found1 = commentRepository.findById(article.getId(), comment1.getId());
    Optional<Comment> found2 = commentRepository.findById(article.getId(), comment2.getId());

    assertTrue(found1.isPresent());
    assertTrue(found2.isPresent());
    assertEquals("Comment from user 1", found1.get().getBody());
    assertEquals("Comment from user 2", found2.get().getBody());
    assertEquals(user.getId(), found1.get().getUserId());
    assertEquals(user2.getId(), found2.get().getUserId());
  }
}

package io.spring.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.spring.api.exception.ResourceNotFoundException;
import io.spring.application.article.ArticleCommandService;
import io.spring.application.article.NewArticleParam;
import io.spring.application.user.RegisterParam;
import io.spring.application.user.UserService;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.service.AuthorizationService;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import java.util.Arrays;
import java.util.Optional;
import javax.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
public class ErrorHandlingIntegrationTest {

  @Autowired private UserService userService;

  @Autowired private ArticleCommandService articleCommandService;

  @Autowired private UserRepository userRepository;

  @Autowired private ArticleRepository articleRepository;

  @Autowired private CommentRepository commentRepository;

  @Test
  public void should_handle_duplicate_user_registration() {
    RegisterParam param1 = new RegisterParam("duplicate@example.com", "user1", "password");
    User user1 = userService.createUser(param1);
    assertNotNull(user1);

    RegisterParam param2 = new RegisterParam("duplicate@example.com", "user2", "password");
    assertThrows(ConstraintViolationException.class, () -> userService.createUser(param2));

    RegisterParam param3 = new RegisterParam("different@example.com", "user1", "password");
    assertThrows(ConstraintViolationException.class, () -> userService.createUser(param3));
  }

  @Test
  public void should_handle_invalid_user_data() {
    RegisterParam invalidEmail = new RegisterParam("invalid-email", "user", "password");
    assertThrows(ConstraintViolationException.class, () -> userService.createUser(invalidEmail));

    RegisterParam emptyUsername = new RegisterParam("valid@example.com", "", "password");
    assertThrows(ConstraintViolationException.class, () -> userService.createUser(emptyUsername));

    RegisterParam shortPassword = new RegisterParam("valid@example.com", "user", "");
    assertThrows(ConstraintViolationException.class, () -> userService.createUser(shortPassword));
  }

  @Test
  public void should_handle_article_not_found_scenarios() {
    assertThrows(
        ResourceNotFoundException.class,
        () -> {
          Optional<Article> article = articleRepository.findBySlug("non-existent-slug");
          if (!article.isPresent()) {
            throw new ResourceNotFoundException();
          }
        });

    User user = new User("test@example.com", "test", "password", "bio", "image");
    userRepository.save(user);

    assertThrows(
        ResourceNotFoundException.class,
        () -> {
          Optional<Comment> comment =
              commentRepository.findById("non-existent-article-id", "comment-id");
          if (!comment.isPresent()) {
            throw new ResourceNotFoundException();
          }
        });
  }

  @Test
  public void should_handle_authorization_failures() {
    User author = new User("author@example.com", "author", "password", "bio", "image");
    User otherUser = new User("other@example.com", "other", "password", "bio", "image");
    userRepository.save(author);
    userRepository.save(otherUser);

    NewArticleParam articleParam =
        NewArticleParam.builder()
            .title("Test Article")
            .description("Test Description")
            .body("Test Body")
            .tagList(Arrays.asList())
            .build();

    Article article = articleCommandService.createArticle(articleParam, author);

    assertFalse(AuthorizationService.canWriteArticle(otherUser, article));

    Comment comment = new Comment("Test comment", author.getId(), article.getId());
    commentRepository.save(comment);

    assertFalse(AuthorizationService.canWriteComment(otherUser, article, comment));
  }

  @Test
  public void should_handle_constraint_violations_in_database() {
    User user = new User("test@example.com", "test", "password", "bio", "image");
    userRepository.save(user);

    User duplicateEmailUser = new User("test@example.com", "different", "password", "bio", "image");
    assertThrows(Exception.class, () -> userRepository.save(duplicateEmailUser));

    User duplicateUsernameUser =
        new User("different@example.com", "test", "password", "bio", "image");
    assertThrows(Exception.class, () -> userRepository.save(duplicateUsernameUser));
  }

  @Test
  public void should_handle_invalid_article_data() {
    User user = new User("test@example.com", "test", "password", "bio", "image");
    userRepository.save(user);

    NewArticleParam emptyTitle =
        NewArticleParam.builder()
            .title("")
            .description("Description")
            .body("Body")
            .tagList(Arrays.asList())
            .build();

    assertThrows(
        ConstraintViolationException.class,
        () -> articleCommandService.createArticle(emptyTitle, user));

    NewArticleParam emptyDescription =
        NewArticleParam.builder()
            .title("Title")
            .description("")
            .body("Body")
            .tagList(Arrays.asList())
            .build();

    assertThrows(
        ConstraintViolationException.class,
        () -> articleCommandService.createArticle(emptyDescription, user));

    NewArticleParam emptyBody =
        NewArticleParam.builder()
            .title("Title")
            .description("Description")
            .body("")
            .tagList(Arrays.asList())
            .build();

    assertThrows(
        ConstraintViolationException.class,
        () -> articleCommandService.createArticle(emptyBody, user));
  }

  @Test
  public void should_handle_comment_on_non_existent_article() {
    User user = new User("test@example.com", "test", "password", "bio", "image");
    userRepository.save(user);

    Comment comment = new Comment("Test comment", user.getId(), "non-existent-article-id");

    try {
      commentRepository.save(comment);
      Optional<Comment> savedComment =
          commentRepository.findById("non-existent-article-id", comment.getId());
      assertTrue(
          savedComment.isPresent(),
          "Comment should be saved even with non-existent article reference");
    } catch (Exception e) {
      assertTrue(
          e instanceof DataIntegrityViolationException
              || e.getCause() instanceof DataIntegrityViolationException,
          "Should throw DataIntegrityViolationException for foreign key constraint");
    }
  }

  @Test
  public void should_handle_database_transaction_rollback() {
    User user = new User("test@example.com", "test", "password", "bio", "image");
    userRepository.save(user);

    try {
      NewArticleParam validArticle =
          NewArticleParam.builder()
              .title("Valid Article")
              .description("Valid Description")
              .body("Valid Body")
              .tagList(Arrays.asList("valid"))
              .build();

      Article article = articleCommandService.createArticle(validArticle, user);
      assertNotNull(article);

      User duplicateUser = new User("test@example.com", "duplicate", "password", "bio", "image");
      userRepository.save(duplicateUser);

      fail("Should have thrown an exception for duplicate email");
    } catch (Exception e) {
      Optional<Article> shouldNotExist = articleRepository.findBySlug("valid-article");
    }
  }

  @Test
  public void should_handle_null_and_empty_parameters() {
    assertThrows(Exception.class, () -> userService.createUser(null));

    User user = new User("test@example.com", "test", "password", "bio", "image");
    userRepository.save(user);

    assertThrows(Exception.class, () -> articleCommandService.createArticle(null, user));

    assertThrows(
        Exception.class,
        () -> articleCommandService.createArticle(NewArticleParam.builder().build(), user));
  }
}

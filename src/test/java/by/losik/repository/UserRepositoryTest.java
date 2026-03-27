package by.losik.repository;

import by.losik.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserRepositoryTest {

    @Mock
    private EntityManagerFactory emf;

    @Mock
    private EntityManager em;

    @Mock
    private EntityTransaction transaction;

    @Mock
    private TypedQuery<User> typedQuery;

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        Mockito.when(emf.createEntityManager()).thenReturn(em);
        Mockito.when(em.getTransaction()).thenReturn(transaction);
        userRepository = new UserRepository(emf);
    }

    @Test
    void findByUsername_WhenUserExists_ShouldReturnUser() {
        String username = "testuser";
        User expectedUser = createTestUser(username);

        Mockito.when(em.createNamedQuery("User.findByUsername", User.class))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.setParameter("username", username))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.getSingleResult())
                .thenReturn(expectedUser);

        Optional<User> result = userRepository.findByUsername(username);

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(username, result.get().getUsername());
        Mockito.verify(em).createNamedQuery("User.findByUsername", User.class);
        Mockito.verify(typedQuery).setParameter("username", username);
    }

    @Test
    void findByUsername_WhenUserNotExists_ShouldReturnEmpty() {
        String username = "nonexistent";

        Mockito.when(em.createNamedQuery("User.findByUsername", User.class))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.setParameter("username", username))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.getSingleResult())
                .thenThrow(NoResultException.class);

        Optional<User> result = userRepository.findByUsername(username);

        Assertions.assertTrue(result.isEmpty());
        Mockito.verify(em).createNamedQuery("User.findByUsername", User.class);
    }

    @Test
    void findByEmail_WhenUserExists_ShouldReturnUser() {
        String username = "testuser";
        String email = "test@example.com";
        User expectedUser = User.create(username, email, "password123");

        Mockito.when(em.createNamedQuery("User.findByEmail", User.class))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.setParameter("email", email))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.getSingleResult())
                .thenReturn(expectedUser);

        Optional<User> result = userRepository.findByEmail(email);

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(email, result.get().getEmail());
        Mockito.verify(em).createNamedQuery("User.findByEmail", User.class);
    }

    @Test
    void findByEmail_WhenUserNotExists_ShouldReturnEmpty() {
        String email = "nonexistent@example.com";

        Mockito.when(em.createNamedQuery("User.findByEmail", User.class))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.setParameter("email", email))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.getSingleResult())
                .thenThrow(NoResultException.class);

        Optional<User> result = userRepository.findByEmail(email);

        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void validateCredentials_WhenPasswordCorrect_ShouldReturnTrue() {
        String username = "testuser";
        String password = "password123";
        User user = createTestUser(username);

        Mockito.when(em.createNamedQuery("User.findByUsername", User.class))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.setParameter("username", username))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.getSingleResult())
                .thenReturn(user);
        Mockito.when(em.merge(user)).thenReturn(user);

        boolean result = userRepository.validateCredentials(username, password);

        Assertions.assertTrue(result);
        Mockito.verify(transaction).begin();
        Mockito.verify(transaction).commit();
        Assertions.assertNotNull(user.getLastLogin());
    }

    @Test
    void validateCredentials_WhenPasswordWrong_ShouldReturnFalse() {
        String username = "testuser";
        String wrongPassword = "wrongpassword";
        User user = createTestUser(username);

        Mockito.when(em.createNamedQuery("User.findByUsername", User.class))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.setParameter("username", username))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.getSingleResult())
                .thenReturn(user);

        boolean result = userRepository.validateCredentials(username, wrongPassword);

        Assertions.assertFalse(result);
        Mockito.verify(transaction, Mockito.never()).commit();
    }

    @Test
    void createUser_ShouldPersistUser() {
        String username = "newuser";
        String email = "new@example.com";
        String password = "password123";

        Mockito.doNothing().when(em).persist(any(User.class));
        Mockito.doNothing().when(em).flush();

        User result = userRepository.createUser(username, email, password);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(username, result.getUsername());
        Assertions.assertEquals(email, result.getEmail());
        Assertions.assertNotNull(result.getPasswordHash());
        Assertions.assertTrue(result.getPasswordHash().startsWith("$2a$"));
        Mockito.verify(em).persist(any(User.class));
        Mockito.verify(transaction).begin();
        Mockito.verify(transaction).commit();
    }

    @Test
    void updatePassword_WhenUserExists_ShouldUpdatePassword() {
        String username = "testuser";
        String newPassword = "newpassword123";
        User user = createTestUser(username);

        Mockito.when(em.createNamedQuery("User.findByUsername", User.class))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.setParameter("username", username))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.getSingleResult())
                .thenReturn(user);
        Mockito.when(em.merge(user)).thenReturn(user);

        String oldPasswordHash = user.getPasswordHash();

        boolean result = userRepository.updatePassword(username, newPassword);

        Assertions.assertTrue(result);
        Assertions.assertNotEquals(oldPasswordHash, user.getPasswordHash());
        Mockito.verify(em).merge(user);
        Mockito.verify(transaction).begin();
        Mockito.verify(transaction).commit();
    }

    @Test
    void updatePassword_WhenUserNotExists_ShouldReturnFalse() {
        String username = "nonexistent";
        String newPassword = "newpassword123";

        Mockito.when(em.createNamedQuery("User.findByUsername", User.class))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.setParameter("username", username))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.getSingleResult())
                .thenThrow(NoResultException.class);

        boolean result = userRepository.updatePassword(username, newPassword);

        Assertions.assertFalse(result);
        Mockito.verify(transaction, Mockito.never()).commit();
    }

    @Test
    void userExists_WhenUserExists_ShouldReturnTrue() {
        String username = "testuser";
        User user = createTestUser(username);

        Mockito.when(em.createNamedQuery("User.findByUsername", User.class))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.setParameter("username", username))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.getSingleResult())
                .thenReturn(user);

        boolean result = userRepository.userExists(username);

        Assertions.assertTrue(result);
    }

    @Test
    void userExists_WhenUserNotExists_ShouldReturnFalse() {
        String username = "nonexistent";

        Mockito.when(em.createNamedQuery("User.findByUsername", User.class))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.setParameter("username", username))
                .thenReturn(typedQuery);
        Mockito.when(typedQuery.getSingleResult())
                .thenThrow(NoResultException.class);

        boolean result = userRepository.userExists(username);
        Assertions.assertFalse(result);
    }

    private User createTestUser(String username) {
        return User.create(username, username + "@example.com", "password123");
    }
}

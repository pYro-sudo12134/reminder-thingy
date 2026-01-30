package by.losik.repository;

import by.losik.entity.User;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.eclipse.persistence.config.HintValues;
import org.eclipse.persistence.config.QueryHints;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

@Singleton
public class UserRepository {
    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Inject
    public UserRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<User> findByUsername(String username) {
        try {
            User user = entityManager
                    .createNamedQuery("User.findByUsername", User.class)
                    .setParameter("username", username)
                    .setHint(QueryHints.CACHE_USAGE,
                            HintValues.TRUE)
                    .setHint(QueryHints.REFRESH, HintValues.FALSE)
                    .getSingleResult();
            return Optional.of(user);
        } catch (NoResultException e) {
            log.debug("User not found: {}", username);
            return Optional.empty();
        }
    }

    public boolean validateCredentials(String username, String password) {
        return findByUsername(username)
                .map(user -> {
                    boolean isValid = BCrypt.checkpw(password, user.getPasswordHash());
                    if (isValid) {
                        user.setLastLogin(LocalDateTime.now());
                        entityManager.merge(user);
                        log.info("User logged in: {}", username);
                    }
                    return isValid;
                })
                .orElse(false);
    }

    public User createUser(String username, String password, String email) {
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        User user = new User(username, passwordHash, email);
        entityManager.persist(user);
        log.info("User created: {}", username);
        return user;
    }

    public boolean userExists(String username) {
        return findByUsername(username).isPresent();
    }
}
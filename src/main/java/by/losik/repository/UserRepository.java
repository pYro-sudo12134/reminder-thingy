package by.losik.repository;

import by.losik.entity.User;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

@Singleton
public class UserRepository {
    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);
    private final EntityManagerFactory emf;

    @Inject
    public UserRepository(EntityManagerFactory emf) {
        this.emf = emf;
    }

    private EntityManager getEntityManager() {
        return emf.createEntityManager();
    }

    @Transactional
    public Optional<User> findByUsername(String username) {
        try (EntityManager em = getEntityManager()) {
            log.info("FINDING USER BY USERNAME: {}", username);

            User user = em
                    .createNamedQuery("User.findByUsername", User.class)
                    .setParameter("username", username)
                    .getSingleResult();

            log.info("USER FOUND: {}", user.getUsername());
            return Optional.of(user);

        } catch (NoResultException e) {
            log.error("USER NOT FOUND: {}", username);
            return Optional.empty();
        } catch (Exception e) {
            log.error("ERROR in findByUsername: ", e);
            return Optional.empty();
        }
    }

    @Transactional
    public boolean validateCredentials(String username, String password) {
        EntityManager em = getEntityManager();
        try {
            log.info("=== VALIDATE CREDENTIALS START ===");
            log.info("Username: '{}'", username);
            log.info("Password: '{}'", password);

            em.getTransaction().begin();

            Optional<User> userOpt = findByUsername(username);

            if (userOpt.isEmpty()) {
                log.error("USER NOT FOUND: {}", username);
                em.getTransaction().rollback();
                log.info("=== VALIDATE CREDENTIALS END (user not found) ===");
                return false;
            }

            User user = userOpt.get();
            log.info("User found: {}", user.getUsername());
            log.info("Stored hash: {}", user.getPasswordHash());
            log.info("Hash length: {}", user.getPasswordHash().length());

            boolean isValid = BCrypt.checkpw(password, user.getPasswordHash());
            log.info("BCrypt.checkpw result: {}", isValid);

            if (isValid) {
                user.setLastLogin(LocalDateTime.now());
                em.merge(user);
                log.info("User logged in: {}", username);
            }

            em.getTransaction().commit();
            log.info("=== VALIDATE CREDENTIALS END (result={}) ===", isValid);
            return isValid;

        } catch (Exception e) {
            log.error("ERROR in validateCredentials: ", e);
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return false;
        } finally {
            em.close();
        }
    }

    @Transactional
    public User createUser(String username, String email, String password) {
        EntityManager em = getEntityManager();
        try {
            em.getTransaction().begin();

            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
            User user = new User(username, email, passwordHash);

            log.info("Persisting user: {}", username);
            em.persist(user);

            em.flush();

            em.getTransaction().commit();

            log.info("User created with ID: {}", user.getId());
            return user;
        } catch (Exception e) {
            log.error("Error creating user: ", e);
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    @Transactional
    public Optional<User> findByEmail(String email) {
        try (EntityManager em = getEntityManager()) {
            log.info("FINDING USER BY EMAIL: {}", email);

            User user = em
                    .createNamedQuery("User.findByEmail", User.class)
                    .setParameter("email", email)
                    .getSingleResult();

            log.info("USER FOUND BY EMAIL: {}", user.getUsername());
            return Optional.of(user);

        } catch (NoResultException e) {
            log.info("USER NOT FOUND BY EMAIL: {}", email);
            return Optional.empty();
        } catch (Exception e) {
            log.error("ERROR in findByEmail: ", e);
            return Optional.empty();
        }
    }

    @Transactional
    public boolean updatePassword(String username, String newPassword) {
        EntityManager em = getEntityManager();
        try {
            em.getTransaction().begin();

            Optional<User> userOpt = findByUsername(username);
            if (userOpt.isEmpty()) {
                log.warn("Cannot update password - user not found: {}", username);
                em.getTransaction().rollback();
                return false;
            }

            User user = userOpt.get();
            String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            user.setPasswordHash(newHash);
            user.setUpdatedAt(LocalDateTime.now());

            em.merge(user);
            em.getTransaction().commit();

            log.info("Password updated for user: {}", username);
            return true;

        } catch (Exception e) {
            log.error("Error updating password for user: {}", username, e);
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return false;
        } finally {
            em.close();
        }
    }

    public boolean userExists(String username) {
        return findByUsername(username).isPresent();
    }
}
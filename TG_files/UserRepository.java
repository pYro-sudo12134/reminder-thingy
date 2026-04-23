package by.losik.repository;

import by.losik.entity.User;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Репозиторий для работы с пользователями в базе данных PostgreSQL.
 * <p>
 * Предоставляет CRUD операции для сущности {@link User}:
 * <ul>
 *     <li>Поиск по username и email</li>
 *     <li>Создание нового пользователя с хэшированием пароля</li>
 *     <li>Валидация учётных данных (логин/пароль)</li>
 *     <li>Обновление пароля</li>
 * </ul>
 * <p>
 * Использует JPA (EclipseLink) для доступа к базе данных.
 * Все методы, изменяющие данные, аннотированы {@code @Transactional} для обеспечения целостности.
 *
 * @see User
 * @see EntityManager
 */
@Singleton
public class UserRepository {
    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);
    private final EntityManagerFactory emf;

    /**
     * Создаёт репозиторий с фабрикой EntityManager.
     *
     * @param emf фабрика EntityManager для создания подключений к БД
     */
    @Inject
    public UserRepository(EntityManagerFactory emf) {
        this.emf = emf;
    }

    private EntityManager getEntityManager() {
        return emf.createEntityManager();
    }

    /**
     * Находит пользователя по имени пользователя.
     *
     * @param username имя пользователя для поиска
     * @return {@link Optional} с найденным пользователем или пустой, если не найден
     */
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
            log.error("ERROR in findByUsername: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Проверяет учётные данные пользователя (логин/пароль).
     * <p>
     * При успешной проверке обновляет {@code lastLogin} пользователя.
     *
     * @param username имя пользователя
     * @param password пароль в открытом виде
     * @return true если учётные данные верны
     */
    public boolean validateCredentials(String username, String password) {
        EntityManager em = getEntityManager();
        try {
            em.getTransaction().begin();

            Optional<User> userOpt = findByUsername(username);

            if (userOpt.isEmpty()) {
                log.info("USER NOT FOUND: {}", username);
                em.getTransaction().rollback();
                return false;
            }

            User user = userOpt.get();
            log.info("User found: {}", user.getUsername());

            boolean isValid = user.checkPassword(password);
            log.info("Password validation result: {}", isValid);

            if (isValid) {
                user.setLastLogin(LocalDateTime.now());
                em.merge(user);
                log.info("User logged in: {}", username);
            }

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

    /**
     * Создаёт нового пользователя с хэшированием пароля.
     * <p>
     * Использует {@link User#create(String, String, String)} для безопасного создания.
     *
     * @param username имя пользователя (3-50 символов)
     * @param email email пользователя
     * @param password пароль в открытом виде (будет захэширован)
     * @return созданный пользователь с установленным ID
     * @throws RuntimeException если не удалось создать пользователя
     */
    @Transactional
    public User createUser(String username, String email, String password) {
        EntityManager em = getEntityManager();
        try {
            em.getTransaction().begin();

            User user = User.create(username, email, password);

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

    /**
     * Находит пользователя по email.
     *
     * @param email email для поиска
     * @return {@link Optional} с найденным пользователем или пустой, если не найден
     */
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

    /**
     * Обновляет пароль пользователя.
     * <p>
     * Пароль хэшируется через {@link User#updatePassword(String)}.
     *
     * @param username имя пользователя
     * @param newPassword новый пароль в открытом виде (будет захэширован)
     * @return true если пароль успешно обновлён
     */
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
            user.updatePassword(newPassword);

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

    /**
     * Проверяет существование пользователя по имени.
     *
     * @param username имя пользователя для проверки
     * @return true если пользователь существует
     */
    public boolean userExists(String username) {
        return findByUsername(username).isPresent();
    }

    public Optional<User> findById(Long id) {
        try (EntityManager em = getEntityManager()) {
            return Optional.ofNullable(em.find(User.class, id));
        } catch (Exception e) {
            log.error("Error in findById: ", e);
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<User> findByTelegramBindingCode(String code) {
        try (EntityManager em = getEntityManager()) {
            var query = em.createQuery(
                "SELECT u FROM User u WHERE u.telegramBindingCode = :code", User.class);
            query.setParameter("code", code);
            return query.getResultList().stream().findFirst();
        } catch (Exception e) {
            log.error("Error in findByTelegramBindingCode: ", e);
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<User> findByTelegramChatId(Long chatId) {
        try (EntityManager em = getEntityManager()) {
            var query = em.createQuery(
                "SELECT u FROM User u WHERE u.telegramChatId = :chatId", User.class);
            query.setParameter("chatId", chatId);
            return query.getResultList().stream().findFirst();
        } catch (Exception e) {
            log.error("Error in findByTelegramChatId: ", e);
            return Optional.empty();
        }
    }

    @Transactional
    public boolean update(User user) {
        EntityManager em = getEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(user);
            em.getTransaction().commit();
            return true;
        } catch (Exception e) {
            log.error("Error in update: ", e);
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return false;
        } finally {
            em.close();
        }
    }
}
package by.losik.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.persistence.annotations.Cache;
import org.eclipse.persistence.annotations.CacheCoordinationType;
import org.eclipse.persistence.annotations.CacheType;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Сущность пользователя системы Voice Reminder.
 * <p>
 * Представляет пользователя в базе данных PostgreSQL с партиционированием по кварталам.
 * Пароли хранятся в хэшированном виде (BCrypt).
 * <p>
 * Кэшируется в EclipseLink с настройками:
 * <ul>
 *     <li>Тип: SOFT (мягкие ссылки)</li>
 *     <li>Размер: 1000 записей</li>
 *     <li>Время жизни: 30 минут</li>
 * </ul>
 *
 * @see by.losik.repository.UserRepository
 */
@Entity
@Table(name = "users", schema = "voice_schema")
@NamedQueries({
        @NamedQuery(name = "User.findByUsername",
                query = "SELECT u FROM User u WHERE u.username = :username"),
        @NamedQuery(name = "User.findAllActive",
                query = "SELECT u FROM User u WHERE u.isActive = true"),
        @NamedQuery(name = "User.findByEmail",
            query = "SELECT u FROM User u WHERE u.email = :email"),
        @NamedQuery(name = "User.findByTelegramBindingCode",
            query = "SELECT u FROM User u WHERE u.telegramBindingCode = :code"),
        @NamedQuery(name = "User.findByTelegramChatId",
                query = "SELECT u FROM User u WHERE u.telegramChatId = :chatId")
})
@Cache(
    type = CacheType.SOFT,
    size = 1000,
    expiry = 1800000,
    alwaysRefresh = false,
    disableHits = false,
    coordinationType = CacheCoordinationType.SEND_NEW_OBJECTS_WITH_CHANGES
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @NotBlank(message = "Password hash is required")
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "telegram_binding_code", length = 10)
    private String telegramBindingCode;

    @Column(name = "telegram_code_expiry")
    private LocalDateTime telegramCodeExpiry;

    /**
     * Конструктор по умолчанию для JPA.
     */
    public User() {}

    /**
     * Приватный конструктор для использования factory method.
     * @param username Имя пользователя
     * @param email Email
     * @param passwordHash Хэш пароля (BCrypt)
     */
    private User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /**
     * Создаёт нового пользователя с хэшированием пароля.
     * @param username Имя пользователя (3-50 символов)
     * @param email Email пользователя
     * @param rawPassword Пароль в открытом виде
     * @return Новый пользователь с захэшированным паролем
     */
    public static User create(String username, String email, String rawPassword) {
        String passwordHash = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        User user = new User(username, email, passwordHash);
        user.prePersist();
        return user;
    }

    /**
     * Получает ID пользователя.
     * @return ID пользователя
     */
    public Long getId() { return id; }

    /**
     * Получает имя пользователя.
     * @return имя пользователя
     */
    public String getUsername() { return username; }

    /**
     * Получает email пользователя.
     * @return email
     */
    public String getEmail() { return email; }

    /**
     * Получает хэш пароля.
     * @return хэш пароля (BCrypt)
     */
    public String getPasswordHash() { return passwordHash; }

    /**
     * Получает время создания записи.
     * @return время создания
     */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /**
     * Получает время последнего обновления.
     * @return время обновления
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * Проверяет, активен ли пользователь.
     * @return true если активен
     */
    public boolean getIsActive() { return isActive; }

    /**
     * Получает время последнего входа.
     * @return время последнего входа или null
     */
    public LocalDateTime getLastLogin() { return lastLogin; }

    /**
     * Устанавливает имя пользователя.
     * @param username имя пользователя
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Устанавливает email пользователя.
     * @param email email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Устанавливает хэш пароля.
     * @param passwordHash хэш пароля
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Устанавливает время создания.
     * @param createdAt время создания
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * Устанавливает время обновления.
     * @param updatedAt время обновления
     */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Устанавливает статус активности.
     * @param isActive true если активен
     */
    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }

    /**
     * Устанавливает время последнего входа.
     * @param lastLogin время последнего входа
     */
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    /**
     * JPA callback: обновляет updatedAt перед обновлением сущности.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * JPA callback: устанавливает createdAt и updatedAt перед созданием сущности.
     */
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Возвращает строковое представление пользователя.
     * <p>
     * Пароль не включается в вывод из соображений безопасности.
     *
     * @return строка с id, username, email и isActive
     */
    @Override
    public String toString() {
        return "User{id=%d, username='%s', email='%s', isActive=%s}"
                .formatted(id, username, email, isActive);
    }

    /**
     * Сравнивает пользователей по username (business key).
     *
     * @param o объект для сравнения
     * @return true если username совпадают
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(username, user.username);
    }

    /**
     * Вычисляет хэш на основе username.
     *
     * @return хэш username
     */
    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    /**
     * Проверяет соответствие пароля хэшу.
     * @param rawPassword Пароль в открытом виде
     * @return true если пароль совпадает
     */
    public boolean checkPassword(String rawPassword) {
        return BCrypt.checkpw(rawPassword, passwordHash);
    }

    /**
     * Обновляет пароль пользователя с хэшированием.
     * @param rawPassword Новый пароль в открытом виде
     */
    public void updatePassword(String rawPassword) {
        this.passwordHash = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    public Long getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(Long telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public String getTelegramBindingCode() {
        return telegramBindingCode;
    }

    public void setTelegramBindingCode(String telegramBindingCode) {
        this.telegramBindingCode = telegramBindingCode;
    }

    public LocalDateTime getTelegramCodeExpiry() {
        return telegramCodeExpiry;
    }

    public void setTelegramCodeExpiry(LocalDateTime telegramCodeExpiry) {
        this.telegramCodeExpiry = telegramCodeExpiry;
    }
}
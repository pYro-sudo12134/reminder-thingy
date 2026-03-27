package by.losik.config;

import com.google.inject.Singleton;

import java.util.Set;

/**
 * Конфигурация фильтра аутентификации (SessionAuthFilter).
 * <p>
 * Определяет список публичных путей, не требующих аутентификации.
 * Эти пути доступны без активной сессии.
 *
 * @see by.losik.filter.SessionAuthFilter
 */
@Singleton
public class AuthFilterConfig {

    /**
     * Список публичных путей.
     * <p>
     * Пути могут быть:
     * <ul>
     *     <li>Точными совпадениями (например, "test", "health")</li>
     *     <li>Префиксами (например, "auth/login" включает "auth/login/anything")</li>
     * </ul>
     */
    private final Set<String> publicPaths;

    /**
     * Создаёт конфигурацию с настройками по умолчанию.
     */
    public AuthFilterConfig() {
        this.publicPaths = Set.of(
                "test",
                "auth/login",
                "auth/logout",
                "auth/register",
                "auth/me",
                "health",
                "metrics",
                "auth/password/forgot",
                "auth/password/reset",
                "auth/password/validate",
                "auth/password/reset-form"
        );
    }

    /**
     * Проверяет, является ли путь публичным.
     *
     * @param path путь для проверки
     * @return true если путь публичный (не требует аутентификации)
     */
    public boolean isPublicPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return true;
        }

        if (publicPaths.contains(path)) {
            return true;
        }

        for (String publicPath : publicPaths) {
            if (path.startsWith(publicPath + "/") || path.equals(publicPath)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Получает набор публичных путей.
     *
     * @return неизменяемый набор публичных путей
     */
    public Set<String> getPublicPaths() {
        return publicPaths;
    }
}

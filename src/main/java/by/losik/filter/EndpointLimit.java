package by.losik.filter;

import java.util.Objects;

/**
 * Лимиты запросов для категории эндпоинтов.
 * <p>
 * Определяет максимальное количество запросов в минуту, час и день
 * для определённой категории эндпоинтов (upload, api, stats, general).
 */
public class EndpointLimit {
    private final int maxRequestsPerMinute;
    private final int maxRequestsPerHour;
    private final int maxRequestsPerDay;

    /**
     * Создаёт лимиты для категории эндпоинтов.
     *
     * @param perMinute максимальное количество запросов в минуту
     * @param perHour максимальное количество запросов в час
     * @param perDay максимальное количество запросов в день
     * @throws IllegalArgumentException если любой из лимитов отрицательный
     */
    public EndpointLimit(int perMinute, int perHour, int perDay) {
        if (perMinute < 0 || perHour < 0 || perDay < 0) {
            throw new IllegalArgumentException("Rate limits cannot be negative");
        }
        this.maxRequestsPerMinute = perMinute;
        this.maxRequestsPerHour = perHour;
        this.maxRequestsPerDay = perDay;
    }

    /**
     * Получает лимит запросов в минуту.
     *
     * @return максимальное количество запросов в минуту
     */
    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    /**
     * Получает лимит запросов в час.
     *
     * @return максимальное количество запросов в час
     */
    public int getMaxRequestsPerHour() {
        return maxRequestsPerHour;
    }

    /**
     * Получает лимит запросов в день.
     *
     * @return максимальное количество запросов в день
     */
    public int getMaxRequestsPerDay() {
        return maxRequestsPerDay;
    }

    /**
     * Возвращает строковое представление лимитов.
     *
     * @return строка в формате "EndpointLimit{min=X, hour=Y, day=Z}"
     */
    @Override
    public String toString() {
        return "EndpointLimit{min=%d, hour=%d, day=%d}"
                .formatted(maxRequestsPerMinute, maxRequestsPerHour, maxRequestsPerDay);
    }

    /**
     * Сравнивает лимиты с другим объектом.
     *
     * @param o объект для сравнения
     * @return true если все лимиты совпадают
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointLimit that = (EndpointLimit) o;
        return maxRequestsPerMinute == that.maxRequestsPerMinute
                && maxRequestsPerHour == that.maxRequestsPerHour
                && maxRequestsPerDay == that.maxRequestsPerDay;
    }

    /**
     * Вычисляет хэш на основе лимитов.
     *
     * @return хэш значение
     */
    @Override
    public int hashCode() {
        return Objects.hash(maxRequestsPerMinute, maxRequestsPerHour, maxRequestsPerDay);
    }
}
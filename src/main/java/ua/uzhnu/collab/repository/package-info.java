/**
 * Шар доступу до даних (Repository).
 *
 * <p>Усі репозиторії використовують {@link org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate}
 * із іменованими параметрами для безпечної побудови SQL-запитів.
 * JPA/Hibernate не використовується — SQL є явним і передбачуваним.
 *
 * <p>Конвенції:
 * <ul>
 *   <li>{@code save(entity)} — insert якщо {@code id == null}, інакше update
 *   <li>{@code findBy*()} — повертають повністю ініціалізовані сутності (JOIN підтягує зв'язки)
 *   <li>{@code existsBy*()} — повертають {@code boolean} через {@code COUNT(*)}
 *   <li>{@code countBy*()} — агрегатні запити для статистики
 * </ul>
 */
package ua.uzhnu.collab.repository;

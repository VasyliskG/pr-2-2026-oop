/**
 * POJO-сутності доменної моделі.
 *
 * <p>Кожна сутність відповідає таблиці PostgreSQL. Замість JPA/Hibernate
 * використовується ручний маппінг через {@link org.springframework.jdbc.core.RowMapper}
 * у відповідних репозиторіях.
 *
 * <ul>
 *   <li>{@link ua.uzhnu.collab.entity.User} — зареєстрований користувач
 *   <li>{@link ua.uzhnu.collab.entity.Team} — команда
 *   <li>{@link ua.uzhnu.collab.entity.TeamMember} — учасник команди з роллю
 *   <li>{@link ua.uzhnu.collab.entity.Task} — задача канбан-дошки
 *   <li>{@link ua.uzhnu.collab.entity.TaskAssignee} — призначення виконавця на задачу
 *   <li>{@link ua.uzhnu.collab.entity.TaskComment} — коментар до задачі
 *   <li>{@link ua.uzhnu.collab.entity.ChatMessage} — повідомлення командного чату
 *   <li>{@link ua.uzhnu.collab.entity.AppFile} — метадані завантаженого файлу
 * </ul>
 */
package ua.uzhnu.collab.entity;

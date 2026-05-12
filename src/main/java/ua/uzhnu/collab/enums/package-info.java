/**
 * Перерахування (enum) платформи.
 *
 * <p>Значення відповідають PostgreSQL custom enum-типам:
 * {@code task_status}, {@code task_priority}, {@code team_role}.
 * При зчитуванні з БД конвертуються через {@code TaskStatus.valueOf(rs.getString(...))}.
 */
package ua.uzhnu.collab.enums;

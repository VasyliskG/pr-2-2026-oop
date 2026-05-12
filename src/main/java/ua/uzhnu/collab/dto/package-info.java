/**
 * DTO (Data Transfer Objects) для обміну між шарами застосунку.
 *
 * <p>Усі DTO реалізовано як Java Records (незмінні, з автогенерацією
 * конструктора, гетерів, {@code equals}, {@code hashCode}, {@code toString}).
 * Визначені у єдиному файлі {@link ua.uzhnu.collab.dto.Dtos} як вкладені записи.
 *
 * <p>Правило: сервісний шар повертає DTO, сутності не виходять за межі сервісу.
 */
package ua.uzhnu.collab.dto;

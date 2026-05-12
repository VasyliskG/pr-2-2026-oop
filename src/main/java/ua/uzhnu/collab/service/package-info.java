/**
 * Сервісний шар бізнес-логіки.
 *
 * <p>Усі сервіси анотовані {@code @Transactional(readOnly = true)} за замовчуванням;
 * методи, що модифікують дані, перевизначають транзакцію на {@code @Transactional}.
 * Час виконання кожного публічного методу логується через
 * {@link ua.uzhnu.collab.aspect.LoggingAspect}.
 *
 * <ul>
 *   <li>{@link ua.uzhnu.collab.service.UserService} — реєстрація, автентифікація, профіль
 *   <li>{@link ua.uzhnu.collab.service.TeamService} — команди, учасники, статистика
 *   <li>{@link ua.uzhnu.collab.service.TaskService} — задачі, статуси, виконавці, коментарі
 *   <li>{@link ua.uzhnu.collab.service.FileService} — метадані файлів
 *   <li>{@link ua.uzhnu.collab.service.ChatService} — повідомлення чату
 *   <li>{@link ua.uzhnu.collab.service.DtoMapper} — перетворення сутностей у DTO
 * </ul>
 */
package ua.uzhnu.collab.service;

/**
 * JavaFX FXML-контролери (шар View в MVVM).
 *
 * <p>Контролери обробляють події UI та делегують логіку до ViewModels або сервісів.
 * Усі операції з БД виконуються у фонових потоках через {@link javafx.concurrent.Task}
 * для збереження відзивності UI.
 *
 * <ul>
 *   <li>{@link ua.uzhnu.collab.controller.LoginController} — екран входу
 *   <li>{@link ua.uzhnu.collab.controller.RegisterController} — реєстрація
 *   <li>{@link ua.uzhnu.collab.controller.DashboardController} — список команд
 *   <li>{@link ua.uzhnu.collab.controller.TeamViewControllerV2} — канбан, чат, файли, учасники
 *   <li>{@link ua.uzhnu.collab.controller.TaskDetailController} — деталі задачі (модальний діалог)
 *   <li>{@link ua.uzhnu.collab.controller.DialogHelper} — фабрика діалогових вікон
 * </ul>
 */
package ua.uzhnu.collab.controller;

/**
 * ViewModels для JavaFX-екранів (патерн MVVM).
 *
 * <p>ViewModels містять JavaFX {@link javafx.beans.property.Property}-поля для реактивного
 * зв'язування з UI-елементами через {@code bind()} / {@code bindBidirectional()}.
 * Асинхронні операції з БД повертаються у вигляді {@link javafx.concurrent.Task},
 * які запускаються контролерами у фонових потоках.
 */
package ua.uzhnu.collab.viewmodel;

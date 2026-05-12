package ua.uzhnu.collab.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.dto.Dtos.UserDto;

/**
 * Контекст поточної сесії користувача.
 *
 * <p>Зберігає дані автентифікованого користувача на час роботи застосунку. Оскільки десктопний
 * застосунок обслуговує одного користувача, використовується singleton-scope (за замовчуванням у
 * Spring).
 */
@Component
@Getter
@Setter
public class SessionContext {

  private UserDto currentUser;

  /** Перевіряє, чи користувач автентифікований. */
  public boolean isAuthenticated() {
    return currentUser != null;
  }

  /** Завершує сесію (вихід з облікового запису). */
  public void logout() {
    currentUser = null;
  }

  /**
   * Ідентифікатор поточного користувача.
   *
   * @throws IllegalStateException якщо користувач не автентифікований
   */
  public Long getCurrentUserId() {
    if (currentUser == null) {
      throw new IllegalStateException("Користувач не автентифікований");
    }
    return currentUser.id();
  }
}

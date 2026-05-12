package ua.uzhnu.collab.entity;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Команда — основна організаційна одиниця платформи.
 *
 * <p>Команда об'єднує учасників ({@link TeamMember}) і містить задачі ({@link Task}),
 * повідомлення чату ({@link ChatMessage}) та файли ({@link AppFile}).
 * Творець команди автоматично отримує роль {@link ua.uzhnu.collab.enums.TeamRole#OWNER}.
 *
 * @see ua.uzhnu.collab.service.TeamService
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Team {

  /** Первинний ключ (генерується БД). */
  private Long id;

  /**
   * Назва команди (унікальна).
   * Обмеження: не порожня, максимум 100 символів.
   */
  @NotBlank(message = "Назва команди не може бути порожньою")
  @Size(max = 100, message = "Назва команди: максимум 100 символів")
  private String name;

  /** Опис команди (може бути {@code null}). */
  private String description;

  /** Користувач, який створив команду. Є єдиним OWNER при створенні. */
  private User createdBy;

  /** Час створення (встановлюється БД). */
  private LocalDateTime createdAt;

  /** Час останнього оновлення (встановлюється БД). */
  private LocalDateTime updatedAt;
}

package ua.uzhnu.collab.entity;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class User {

  private Long id;

  @NotBlank(message = "Ім'я користувача не може бути порожнім")
  @Size(min = 3, max = 50, message = "Ім'я користувача: від 3 до 50 символів")
  private String username;

  @NotBlank(message = "Електронна пошта не може бути порожньою")
  @Email(message = "Некоректний формат електронної пошти")
  private String email;

  @NotBlank(message = "Хеш пароля не може бути порожнім")
  private String passwordHash;

  @NotBlank(message = "Повне ім'я не може бути порожнім")
  private String fullName;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}

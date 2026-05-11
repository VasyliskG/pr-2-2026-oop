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
public class Team {

  private Long id;

  @NotBlank(message = "Назва команди не може бути порожньою")
  @Size(max = 100, message = "Назва команди: максимум 100 символів")
  private String name;

  private String description;

  private User createdBy;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}

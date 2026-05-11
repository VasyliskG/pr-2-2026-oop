package ua.uzhnu.collab.entity;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "parentMessage")
@EqualsAndHashCode(of = "id")
public class ChatMessage {

  private Long id;
  private Team team;
  private User user;

  @NotBlank(message = "Текст повідомлення не може бути порожнім")
  private String content;

  private ChatMessage parentMessage;

  private LocalDateTime createdAt;
}

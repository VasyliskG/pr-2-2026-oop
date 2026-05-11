package ua.uzhnu.collab.entity;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class AppFile {

  private Long id;
  private Team team;
  private Task task;
  private User uploadedBy;

  @NotBlank(message = "Назва файлу не може бути порожньою")
  private String fileName;

  @NotBlank private String storagePath;

  @Positive(message = "Розмір файлу повинен бути додатним числом")
  private Long fileSize;

  @NotBlank private String mimeType;

  private LocalDateTime uploadedAt;
}

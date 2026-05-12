package ua.uzhnu.collab.entity;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Метадані файлу, прикріпленого до команди або задачі.
 *
 * <p>Фізичний вміст файлу зберігається на диску за шляхом {@code storagePath}.
 * Якщо {@link #task} дорівнює {@code null}, файл є спільним ресурсом команди.
 * При видаленні задачі ({@code ON DELETE SET NULL}) прив'язка до задачі анулюється,
 * але файл залишається в команді.
 *
 * @see ua.uzhnu.collab.service.FileService
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class AppFile {

  /** Первинний ключ (генерується БД). */
  private Long id;

  /** Команда-власник файлу. */
  private Team team;

  /**
   * Задача, до якої прикріплено файл.
   * {@code null} означає спільний файл команди.
   */
  private Task task;

  /** Користувач, який завантажив файл. */
  private User uploadedBy;

  /**
   * Оригінальна назва файлу з розширенням.
   * Не порожня.
   */
  @NotBlank(message = "Назва файлу не може бути порожньою")
  private String fileName;

  /**
   * Шлях до файлу на диску (відносно кореня застосунку).
   * Формат: {@code storage/team_{teamId}/{fileName}}.
   */
  @NotBlank
  private String storagePath;

  /**
   * Розмір файлу у байтах.
   * Повинен бути додатним числом.
   */
  @Positive(message = "Розмір файлу повинен бути додатним числом")
  private Long fileSize;

  /**
   * MIME-тип файлу (наприклад, {@code application/pdf}, {@code image/png}).
   * Значення {@code application/octet-stream} використовується як fallback.
   */
  @NotBlank
  private String mimeType;

  /** Час завантаження файлу (встановлюється БД). */
  private LocalDateTime uploadedAt;
}

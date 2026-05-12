package ua.uzhnu.collab.service;

import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.uzhnu.collab.dto.Dtos.FileDto;
import ua.uzhnu.collab.entity.*;
import ua.uzhnu.collab.exception.EntityNotFoundException;
import ua.uzhnu.collab.repository.AppFileRepository;

/**
 * Сервіс бізнес-логіки для сутності {@link AppFile}.
 *
 * <p>Забезпечує збереження метаданих файлів, отримання переліку, видалення. Фізичне
 * завантаження/вивантаження файлів делегується окремому класу StorageService (шар інфраструктури).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class FileService {

  private final AppFileRepository fileRepository;
  private final UserService userService;
  private final TeamService teamService;
  private final TaskService taskService;
  private final DtoMapper mapper;

  // Базова директорія зберігання файлів
  private static final String STORAGE_BASE = "storage";

  /**
   * Зберігає метадані завантаженого файлу. Фізичне збереження виконується на рівні контролера або
   * StorageService.
   */
  @Transactional
  public FileDto saveFileMetadata(
      Long teamId, Long taskId, Long uploaderId, String fileName, long fileSize, String mimeType) {
    teamService.requireMember(teamId, uploaderId);

    Team team = teamService.findTeamById(teamId);
    User uploader = userService.findById(uploaderId);

    Task task = null;
    if (taskId != null) {
      task = taskService.findTaskById(taskId);
      // Перевірка: задача має належати тій самій команді
      if (!task.getTeam().getId().equals(teamId)) {
        throw new IllegalArgumentException("Задача не належить до вказаної команди");
      }
    }

    String storagePath = buildStoragePath(teamId, fileName);

    AppFile file =
        AppFile.builder()
            .team(team)
            .task(task)
            .uploadedBy(uploader)
            .fileName(fileName)
            .storagePath(storagePath)
            .fileSize(fileSize)
            .mimeType(mimeType)
            .build();

    file = fileRepository.save(file);
    log.info(
        "Збережено файл '{}' ({} байт) у команді '{}' від {}",
        fileName,
        fileSize,
        team.getName(),
        uploader.getUsername());
    return mapper.toFileDto(file);
  }

  /** Усі файли команди. */
  public List<FileDto> getByTeam(Long teamId) {
    return fileRepository.findByTeamIdOrderByUploadedAtDesc(teamId).stream()
        .map(mapper::toFileDto)
        .toList();
  }

  /** Файли конкретної задачі. */
  public List<FileDto> getByTask(Long taskId) {
    return fileRepository.findByTaskId(taskId).stream().map(mapper::toFileDto).toList();
  }

  /** Файли спільного простору команди (без прив'язки до задач). */
  public List<FileDto> getSharedFiles(Long teamId) {
    return fileRepository.findByTeamIdAndTaskIsNull(teamId).stream()
        .map(mapper::toFileDto)
        .toList();
  }

  /** Видаляє метадані файлу (фізичне видалення — окремо). */
  @Transactional
  public String deleteFile(Long fileId, Long userId) {
    AppFile file =
        fileRepository
            .findById(fileId)
            .orElseThrow(() -> new EntityNotFoundException("Файл", fileId));

    teamService.requireMember(file.getTeam().getId(), userId);

    String path = file.getStoragePath();
    fileRepository.delete(file);
    log.info("Видалено метадані файлу id={}: '{}'", fileId, file.getFileName());
    return path; // повертає шлях для фізичного видалення
  }

  /** Загальний розмір файлів команди у байтах. */
  public long getTotalSize(Long teamId) {
    return fileRepository.sumFileSizeByTeamId(teamId);
  }

  // =====================================================================
  // ВНУТРІШНІ МЕТОДИ
  // =====================================================================

  private String buildStoragePath(Long teamId, String fileName) {
    return Path.of(STORAGE_BASE, "team_" + teamId, fileName).toString();
  }
}

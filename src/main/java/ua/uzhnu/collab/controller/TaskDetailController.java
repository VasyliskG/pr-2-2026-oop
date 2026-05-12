package ua.uzhnu.collab.controller;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.SessionContext;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;
import ua.uzhnu.collab.service.FileService;
import ua.uzhnu.collab.service.TaskService;
import ua.uzhnu.collab.service.TeamService;
import ua.uzhnu.collab.service.UserService;

/**
 * Контролер діалогу детального перегляду та редагування задачі.
 *
 * <p>Підтримує: inline-редагування назви/статусу/пріоритету/дедлайну/опису, призначення та зняття
 * виконавців, додавання коментарів.
 */
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class TaskDetailController {

  private final TaskService taskService;
  private final FileService fileService;
  private final TeamService teamService;
  private final SessionContext session;
  private final DialogHelper dialogs;

  private Long taskId;
  private Long teamId;
  private TaskDto currentTask;
  private List<TeamMemberDto> teamMembersCache = new ArrayList<>();

  // Перегляд
  @FXML private Label lblTitle;
  @FXML private Label lblStatus;
  @FXML private Label lblPriority;
  @FXML private Label lblDueDate;
  @FXML private Label lblCreator;
  @FXML private Label lblOverdue;
  @FXML private TextArea txtDescription;

  // Виконавці
  @FXML private ListView<String> listAssignees;

  // Коментарі
  @FXML private ListView<CommentDto> listComments;
  @FXML private TextField txtNewComment;

  // Файли
  @FXML private ListView<FileDto> listFiles;

  // Редагування
  @FXML private TextField txtEditTitle;
  @FXML private ComboBox<TaskPriority> cbEditPriority;
  @FXML private ComboBox<TaskStatus> cbEditStatus;
  @FXML private DatePicker dpEditDueDate;
  @FXML private TextField txtEditDueTime;
  @FXML private VBox editPane;
  @FXML private VBox viewPane;

  private boolean editMode = false;

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

  public void setTask(Long taskId, Long teamId) {
    this.taskId = taskId;
    this.teamId = teamId;
    loadTask();
    loadTeamMembersCache();
  }

  @FXML
  public void initialize() {
    // CellFactory для коментарів
    listComments.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(CommentDto c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                  setText(null);
                  setGraphic(null);
                  return;
                }
                VBox box = new VBox(2);
                Label author = new Label(c.authorName() + " — " + c.createdAt().format(DATE_FMT));
                author.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");
                Label content = new Label(c.content());
                content.setWrapText(true);
                box.getChildren().addAll(author, content);
                setGraphic(box);
                setText(null);
              }
            });

    // CellFactory для файлів
    listFiles.setCellFactory(dialogs.buildFileCellFactory());

    // Контекстне меню для видалення файлу
    ContextMenu fileMenu = new ContextMenu();
    MenuItem deleteFileItem = new MenuItem("Видалити");
    deleteFileItem.setOnAction(e -> handleDeleteFile());
    fileMenu.getItems().add(deleteFileItem);
    listFiles.setContextMenu(fileMenu);

    // Ініціалізація комбобоксів
    cbEditPriority.getItems().addAll(TaskPriority.values());
    cbEditStatus.getItems().addAll(TaskStatus.values());

    // Enter у полі коментаря
    txtNewComment.setOnAction(e -> handleAddComment());

    // Контекстне меню для зняття виконавця
    ContextMenu assigneeMenu = new ContextMenu();
    MenuItem removeItem = new MenuItem("Зняти виконавця");
    removeItem.setOnAction(e -> handleRemoveAssignee());
    assigneeMenu.getItems().add(removeItem);
    listAssignees.setContextMenu(assigneeMenu);
  }

  // =====================================================================
  // ЗАВАНТАЖЕННЯ
  // =====================================================================

  private void loadTask() {
    Task<TaskDto> task =
        new Task<>() {
          @Override
          protected TaskDto call() {
            return taskService.getById(taskId);
          }
        };
    task.setOnSucceeded(
        e -> {
          currentTask = task.getValue();
          populateView();
          loadComments();
          loadFiles();
        });
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  private void loadTeamMembersCache() {
    Task<List<TeamMemberDto>> task =
        new Task<>() {
          @Override
          protected List<TeamMemberDto> call() {
            return teamService.getMembers(teamId);
          }
        };
    task.setOnSucceeded(e -> teamMembersCache = new ArrayList<>(task.getValue()));
    new Thread(task).start();
  }

  private void populateView() {
    lblTitle.setText(currentTask.title());
    lblStatus.setText(currentTask.status().name());
    lblStatus.setStyle(statusStyle(currentTask.status()));
    lblPriority.setText(currentTask.priority().name());
    lblPriority.setStyle(priorityStyle(currentTask.priority()));
    lblCreator.setText("Створив: " + currentTask.creatorName());

    if (currentTask.dueDate() != null) {
      lblDueDate.setText("Дедлайн: " + currentTask.dueDate().format(DATE_FMT));
    } else {
      lblDueDate.setText("Дедлайн: не встановлено");
    }

    lblOverdue.setVisible(currentTask.overdue());
    lblOverdue.setText(currentTask.overdue() ? "⚠ ПРОСТРОЧЕНО" : "");

    txtDescription.setText(currentTask.description() != null ? currentTask.description() : "");
    listAssignees.setItems(FXCollections.observableArrayList(currentTask.assigneeNames()));
  }

  private void loadComments() {
    Task<List<CommentDto>> task =
        new Task<>() {
          @Override
          protected List<CommentDto> call() {
            return taskService.getComments(taskId);
          }
        };
    task.setOnSucceeded(
        e -> listComments.setItems(FXCollections.observableArrayList(task.getValue())));
    new Thread(task).start();
  }

  private void loadFiles() {
    Task<List<FileDto>> task =
        new Task<>() {
          @Override
          protected List<FileDto> call() {
            return fileService.getByTask(taskId);
          }
        };
    task.setOnSucceeded(
        e -> listFiles.setItems(FXCollections.observableArrayList(task.getValue())));
    new Thread(task).start();
  }

  // =====================================================================
  // ФАЙЛИ
  // =====================================================================

  @FXML
  private void handleUploadFile() {
    File file = dialogs.showFileChooser(listFiles.getScene().getWindow());
    if (file == null) return;

    String mime;
    try {
      mime = Files.probeContentType(file.toPath());
    } catch (Exception ex) {
      mime = null;
    }
    if (mime == null) mime = "application/octet-stream";

    String finalMime = mime;
    Task<FileDto> task =
        new Task<>() {
          @Override
          protected FileDto call() {
            return fileService.saveFileMetadata(
                teamId, taskId, session.getCurrentUserId(),
                file.getName(), file.length(), finalMime);
          }
        };
    task.setOnSucceeded(e -> listFiles.getItems().add(task.getValue()));
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  private void handleDeleteFile() {
    FileDto selected = listFiles.getSelectionModel().getSelectedItem();
    if (selected == null) return;

    if (!dialogs.showConfirmation("Видалення файлу", "Видалити «" + selected.fileName() + "»?")) {
      return;
    }

    Task<Void> task =
        new Task<>() {
          @Override
          protected Void call() {
            fileService.deleteFile(selected.id(), session.getCurrentUserId());
            return null;
          }
        };
    task.setOnSucceeded(e -> listFiles.getItems().remove(selected));
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  // =====================================================================
  // ВИКОНАВЦІ
  // =====================================================================

  @FXML
  private void handleAddAssignee() {
    List<TeamMemberDto> available =
        teamMembersCache.stream()
            .filter(m -> !listAssignees.getItems().contains(m.fullName()))
            .toList();
    if (available.isEmpty()) {
      dialogs.showInfo("Виконавці", "Усі учасники команди вже призначені");
      return;
    }

    Dialog<List<TeamMemberDto>> dialog = new Dialog<>();
    dialog.setTitle("Додати виконавців");

    ButtonType addBtn = new ButtonType("Додати", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

    ListView<TeamMemberDto> listPicker = new ListView<>();
    listPicker.getItems().setAll(available);
    listPicker.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    listPicker.setPrefHeight(180);
    listPicker.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(TeamMemberDto m, boolean empty) {
                super.updateItem(m, empty);
                setText(empty || m == null ? null : m.fullName() + " (@" + m.username() + ")");
              }
            });

    dialog
        .getDialogPane()
        .setContent(new VBox(8, new Label("Оберіть виконавців:"), listPicker));
    dialog.getDialogPane().setPrefWidth(320);

    dialog.setResultConverter(
        btn ->
            btn == addBtn
                ? new ArrayList<>(listPicker.getSelectionModel().getSelectedItems())
                : null);

    dialog
        .showAndWait()
        .ifPresent(
            selected -> {
              if (selected.isEmpty()) return;
              Task<Void> task =
                  new Task<>() {
                    @Override
                    protected Void call() {
                      for (TeamMemberDto m : selected) {
                        taskService.assignUser(taskId, m.userId(), session.getCurrentUserId());
                      }
                      return null;
                    }
                  };
              task.setOnSucceeded(
                  e ->
                      selected.forEach(
                          m -> {
                            if (!listAssignees.getItems().contains(m.fullName())) {
                              listAssignees.getItems().add(m.fullName());
                            }
                          }));
              task.setOnFailed(
                  e -> dialogs.showError("Помилка", task.getException().getMessage()));
              new Thread(task).start();
            });
  }

  private void handleRemoveAssignee() {
    String selectedName = listAssignees.getSelectionModel().getSelectedItem();
    if (selectedName == null) return;

    TeamMemberDto member =
        teamMembersCache.stream()
            .filter(m -> m.fullName().equals(selectedName))
            .findFirst()
            .orElse(null);
    if (member == null) {
      dialogs.showError("Помилка", "Учасника не знайдено — оновіть діалог");
      return;
    }

    Long targetUserId = member.userId();
    Task<Void> task =
        new Task<>() {
          @Override
          protected Void call() {
            taskService.unassignUser(taskId, targetUserId, session.getCurrentUserId());
            return null;
          }
        };
    task.setOnSucceeded(e -> listAssignees.getItems().remove(selectedName));
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  // =====================================================================
  // КОМЕНТАРІ
  // =====================================================================

  @FXML
  private void handleAddComment() {
    String text = txtNewComment.getText().trim();
    if (text.isEmpty()) return;

    Task<CommentDto> task =
        new Task<>() {
          @Override
          protected CommentDto call() {
            return taskService.addComment(
                new CommentCreateDto(taskId, text), session.getCurrentUserId());
          }
        };
    task.setOnSucceeded(
        e -> {
          listComments.getItems().add(task.getValue());
          txtNewComment.clear();
          listComments.scrollTo(listComments.getItems().size() - 1);
        });
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  // =====================================================================
  // РЕДАГУВАННЯ
  // =====================================================================

  @FXML
  private void handleToggleEdit() {
    editMode = !editMode;
    if (editMode) {
      txtEditTitle.setText(currentTask.title());
      cbEditPriority.setValue(currentTask.priority());
      cbEditStatus.setValue(currentTask.status());
      if (currentTask.dueDate() != null) {
        dpEditDueDate.setValue(currentTask.dueDate().toLocalDate());
        txtEditDueTime.setText(currentTask.dueDate().format(TIME_FMT));
      } else {
        dpEditDueDate.setValue(null);
        txtEditDueTime.clear();
      }
    }
    txtDescription.setEditable(editMode);
    editPane.setVisible(editMode);
    editPane.setManaged(editMode);
    viewPane.setVisible(!editMode);
    viewPane.setManaged(!editMode);
  }

  @FXML
  private void handleClearDueDate() {
    dpEditDueDate.setValue(null);
    txtEditDueTime.clear();
  }

  @FXML
  private void handleSaveEdit() {
    String newTitle = txtEditTitle.getText().trim();
    if (newTitle.isEmpty()) {
      dialogs.showError("Помилка", "Назва не може бути порожньою");
      return;
    }

    LocalDateTime newDueDate;
    try {
      newDueDate = parseDueDateTime();
    } catch (DateTimeParseException ex) {
      dialogs.showError("Помилка", "Формат часу: HH:mm (наприклад, 23:59)");
      return;
    }

    TaskPriority newPriority = cbEditPriority.getValue();
    TaskStatus newStatus = cbEditStatus.getValue();
    String newDescription = txtDescription.getText();
    LocalDateTime finalDueDate = newDueDate;

    Task<TaskDto> task =
        new Task<>() {
          @Override
          protected TaskDto call() {
            TaskDto updated =
                taskService.update(
                    taskId,
                    newTitle,
                    newDescription,
                    newPriority,
                    finalDueDate,
                    session.getCurrentUserId());
            if (newStatus != currentTask.status()) {
              updated = taskService.changeStatus(taskId, newStatus, session.getCurrentUserId());
            }
            return updated;
          }
        };
    task.setOnSucceeded(
        e -> {
          currentTask = task.getValue();
          populateView();
          handleToggleEdit();
        });
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  @FXML
  private void handleCancelEdit() {
    handleToggleEdit();
  }

  private LocalDateTime parseDueDateTime() {
    LocalDate date = dpEditDueDate.getValue();
    if (date == null) return null;
    String timeStr = txtEditDueTime.getText().trim();
    LocalTime time =
        timeStr.isEmpty()
            ? LocalTime.of(23, 59)
            : LocalTime.parse(timeStr, TIME_FMT); // throws DateTimeParseException if invalid
    return LocalDateTime.of(date, time);
  }

  // =====================================================================
  // СТИЛІ
  // =====================================================================

  private String statusStyle(TaskStatus s) {
    return switch (s) {
      case TODO ->
          "-fx-background-color: #3498db; -fx-text-fill: white; "
              + "-fx-padding: 4 10; -fx-background-radius: 4;";
      case IN_PROGRESS ->
          "-fx-background-color: #f39c12; -fx-text-fill: white; "
              + "-fx-padding: 4 10; -fx-background-radius: 4;";
      case DONE ->
          "-fx-background-color: #27ae60; -fx-text-fill: white; "
              + "-fx-padding: 4 10; -fx-background-radius: 4;";
    };
  }

  private String priorityStyle(TaskPriority p) {
    String color =
        switch (p) {
          case LOW -> "#95a5a6";
          case MEDIUM -> "#3498db";
          case HIGH -> "#e67e22";
          case CRITICAL -> "#c0392b";
        };
    return "-fx-text-fill: " + color + "; -fx-font-weight: bold;";
  }
}

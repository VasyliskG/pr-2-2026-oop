package ua.uzhnu.collab.controller;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.NavigationService;
import ua.uzhnu.collab.config.SessionContext;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;
import ua.uzhnu.collab.enums.TeamRole;
import ua.uzhnu.collab.service.FileService;
import ua.uzhnu.collab.service.TeamService;
import ua.uzhnu.collab.service.UserService;
import ua.uzhnu.collab.viewmodel.TeamViewModel;

/**
 * Розширений контролер екрану команди (v2).
 *
 * <p>Додано у порівнянні з першою версією:
 *
 * <ul>
 *   <li>Подвійний клік на задачу — діалог деталей
 *   <li>Відповіді у чаті (reply)
 *   <li>Завантаження файлів через FileChooser
 *   <li>Додавання/видалення учасників
 *   <li>Підтвердження видалення задачі
 * </ul>
 */
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class TeamViewControllerV2 {

  private final TeamViewModel viewModel;
  private final NavigationService navigation;
  private final DialogHelper dialogs;
  private final FileService fileService;
  private final TeamService teamService;
  private final UserService userService;
  private final SessionContext session;

  private Long replyToMessageId = null;

  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

  // ---- FXML-елементи (ті самі, що й у v1) ----
  @FXML private Label lblTeamName;
  @FXML private Label lblStats;
  @FXML private ProgressIndicator spinner;

  @FXML private ListView<TaskDto> listTodo;
  @FXML private ListView<TaskDto> listInProgress;
  @FXML private ListView<TaskDto> listDone;
  @FXML private TextField txtTaskSearch;

  @FXML private ListView<ChatMessageDto> listChat;
  @FXML private TextField txtChatInput;
  @FXML private Label lblReplyTo;

  @FXML private ListView<FileDto> listFiles;
  @FXML private ListView<TeamMemberDto> listMembers;

  @FXML
  public void initialize() {
    lblTeamName.textProperty().bind(viewModel.teamNameProperty());
    spinner.visibleProperty().bind(viewModel.loadingProperty());

    // Біндінг списків
    listTodo.setItems(viewModel.getTodoTasks());
    listInProgress.setItems(viewModel.getInProgressTasks());
    listDone.setItems(viewModel.getDoneTasks());
    listChat.setItems(viewModel.getChatMessages());
    listFiles.setItems(viewModel.getFiles());
    listMembers.setItems(viewModel.getMembers());
    txtChatInput.textProperty().bindBidirectional(viewModel.chatInputProperty());

    // CellFactory для задач з кольоровим пріоритетом
    setupTaskCells(listTodo);
    setupTaskCells(listInProgress);
    setupTaskCells(listDone);

    // CellFactory для чату з часом та відповіддю
    listChat.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(ChatMessageDto msg, boolean empty) {
                super.updateItem(msg, empty);
                if (empty || msg == null) {
                  setText(null);
                  setGraphic(null);
                  return;
                }

                VBox box = new VBox(1);
                // Відповідь (якщо є)
                if (msg.parentAuthorName() != null) {
                  Label reply =
                      new Label(
                          "  ↳ " + msg.parentAuthorName() + ": " + msg.parentContentPreview());
                  reply.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6;");
                  box.getChildren().add(reply);
                }
                Label content =
                    new Label(
                        msg.createdAt().format(TIME_FMT)
                            + "  "
                            + msg.authorName()
                            + ": "
                            + msg.content());
                content.setWrapText(true);
                if (msg.replyCount() > 0) {
                  content.setText(content.getText() + "  [" + msg.replyCount() + " відп.]");
                }
                box.getChildren().add(content);
                setGraphic(box);
                setText(null);
              }
            });

    // CellFactory для файлів
    listFiles.setCellFactory(dialogs.buildFileCellFactory());

    // Контекстне меню для видалення файлу команди
    ContextMenu fileMenu = new ContextMenu();
    MenuItem deleteFileItem = new MenuItem("Видалити");
    deleteFileItem.setOnAction(e -> handleDeleteFile());
    fileMenu.getItems().add(deleteFileItem);
    listFiles.setContextMenu(fileMenu);

    // CellFactory для учасників з роллю
    listMembers.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(TeamMemberDto m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) {
                  setText(null);
                  setStyle("");
                  return;
                }
                setText(m.fullName() + " (@" + m.username() + ") — " + m.role());
                if (m.role() == TeamRole.OWNER) {
                  setStyle("-fx-font-weight: bold;");
                } else {
                  setStyle("");
                }
              }
            });

    // Подвійний клік на задачу → діалог деталей
    addTaskDoubleClick(listTodo);
    addTaskDoubleClick(listInProgress);
    addTaskDoubleClick(listDone);

    // Контекстне меню для зміни статусу
    setupStatusMenu(listTodo, TaskStatus.TODO);
    setupStatusMenu(listInProgress, TaskStatus.IN_PROGRESS);
    setupStatusMenu(listDone, TaskStatus.DONE);

    // Контекстне меню чату — «Відповісти»
    ContextMenu chatMenu = new ContextMenu();
    MenuItem replyItem = new MenuItem("Відповісти");
    replyItem.setOnAction(
        e -> {
          ChatMessageDto selected = listChat.getSelectionModel().getSelectedItem();
          if (selected != null) {
            replyToMessageId = selected.id();
            lblReplyTo.setText("↳ Відповідь на: " + selected.authorName());
            lblReplyTo.setVisible(true);
            txtChatInput.requestFocus();
          }
        });
    chatMenu.getItems().add(replyItem);
    listChat.setContextMenu(chatMenu);

    // Контекстне меню учасників — «Видалити»
    ContextMenu memberMenu = new ContextMenu();
    MenuItem removeItem = new MenuItem("Видалити з команди");
    removeItem.setOnAction(e -> handleRemoveMember());
    memberMenu.getItems().add(removeItem);
    listMembers.setContextMenu(memberMenu);

    // Слухач статистики
    viewModel
        .statsProperty()
        .addListener(
            (obs, old, s) -> {
              if (s != null) {
                lblStats.setText(
                    String.format(
                        "Учасників: %d  |  Задач: %d (TODO: %d, В роботі: %d, Виконано: %d, Прострочено: %d)  |  Файлів: %d",
                        s.memberCount(),
                        s.totalTasks(),
                        s.todoCount(),
                        s.inProgressCount(),
                        s.doneCount(),
                        s.overdueCount(),
                        s.fileCount()));
              }
            });

    // Пошук
    txtChatInput.setOnAction(e -> handleSendMessage());
    txtTaskSearch.setOnAction(e -> handleSearchTasks());

    // Ініціалізація reply-мітки
    if (lblReplyTo != null) {
      lblReplyTo.setVisible(false);
      lblReplyTo.setOnMouseClicked(e -> cancelReply());
    }

    loadAll();
  }

  // =====================================================================
  // ЗАВАНТАЖЕННЯ
  // =====================================================================

  private void loadAll() {
    viewModel.loadingProperty().set(true);
    var task = viewModel.createLoadAllTask();
    task.setOnSucceeded(e -> viewModel.loadingProperty().set(false));
    task.setOnFailed(
        e -> {
          viewModel.loadingProperty().set(false);
          dialogs.showError("Помилка", task.getException().getMessage());
        });
    new Thread(task).start();
  }

  // =====================================================================
  // ЗАДАЧІ
  // =====================================================================

  @FXML
  private void handleCreateTask() {
    Dialog<TaskCreateDto> dialog = new Dialog<>();
    dialog.setTitle("Нова задача");

    ButtonType createBtn = new ButtonType("Створити", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

    TextField txtTitle = new TextField();
    txtTitle.setPromptText("Назва задачі");
    TextArea txtDesc = new TextArea();
    txtDesc.setPromptText("Опис (необов'язково)");
    txtDesc.setPrefRowCount(3);
    ComboBox<TaskPriority> cbPriority = new ComboBox<>();
    cbPriority.getItems().addAll(TaskPriority.values());
    cbPriority.setValue(TaskPriority.MEDIUM);

    DatePicker dpDueDate = new DatePicker();
    dpDueDate.setPromptText("дд.мм.рррр");
    TextField txtDueTime = new TextField();
    txtDueTime.setPromptText("HH:mm");
    txtDueTime.setPrefWidth(90);
    HBox dueDateBox = new HBox(8, dpDueDate, txtDueTime);

    ListView<TeamMemberDto> listAssignees = new ListView<>();
    listAssignees.getItems().setAll(viewModel.getMembers());
    listAssignees.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    listAssignees.setPrefHeight(110);
    listAssignees.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(TeamMemberDto m, boolean empty) {
                super.updateItem(m, empty);
                setText(empty || m == null ? null : m.fullName() + " (@" + m.username() + ")");
              }
            });

    VBox content =
        new VBox(
            8,
            new Label("Назва:"), txtTitle,
            new Label("Опис:"), txtDesc,
            new Label("Пріоритет:"), cbPriority,
            new Label("Дедлайн:"), dueDateBox,
            new Label("Виконавці (Ctrl/Shift для вибору):"), listAssignees);
    content.setPrefWidth(380);
    dialog.getDialogPane().setContent(content);

    DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

    dialog.setResultConverter(
        btn -> {
          if (btn == createBtn && !txtTitle.getText().isBlank()) {
            LocalDateTime dueDate = null;
            LocalDate date = dpDueDate.getValue();
            if (date != null) {
              String timeStr = txtDueTime.getText().trim();
              LocalTime time;
              try {
                time = timeStr.isEmpty() ? LocalTime.of(23, 59) : LocalTime.parse(timeStr, timeFmt);
              } catch (Exception ex) {
                time = LocalTime.of(23, 59);
              }
              dueDate = LocalDateTime.of(date, time);
            }
            List<Long> assigneeIds =
                listAssignees.getSelectionModel().getSelectedItems().stream()
                    .map(TeamMemberDto::userId)
                    .toList();
            return new TaskCreateDto(
                viewModel.teamProperty().get().id(),
                txtTitle.getText(),
                txtDesc.getText(),
                cbPriority.getValue(),
                dueDate,
                assigneeIds);
          }
          return null;
        });

    dialog
        .showAndWait()
        .ifPresent(
            dto -> {
              var task = viewModel.createTaskAction(dto);
              task.setOnSucceeded(e -> viewModel.onTaskCreated(task.getValue()));
              task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
              new Thread(task).start();
            });
  }

  private void handleSearchTasks() {
    String q = txtTaskSearch.getText().trim();
    if (q.isEmpty()) {
      loadAll();
      return;
    }
    var task = viewModel.searchTasksAction(q);
    task.setOnSucceeded(
        e -> {
          List<TaskDto> results = task.getValue();
          viewModel
              .getTodoTasks()
              .setAll(results.stream().filter(t -> t.status() == TaskStatus.TODO).toList());
          viewModel
              .getInProgressTasks()
              .setAll(results.stream().filter(t -> t.status() == TaskStatus.IN_PROGRESS).toList());
          viewModel
              .getDoneTasks()
              .setAll(results.stream().filter(t -> t.status() == TaskStatus.DONE).toList());
        });
    new Thread(task).start();
  }

  // =====================================================================
  // ЧАТ
  // =====================================================================

  @FXML
  private void handleSendMessage() {
    String content = viewModel.chatInputProperty().get();
    if (content == null || content.isBlank()) return;

    var task = viewModel.sendMessageAction(replyToMessageId);
    task.setOnSucceeded(
        e -> {
          viewModel.onMessageSent(task.getValue());
          cancelReply();
        });
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  private void cancelReply() {
    replyToMessageId = null;
    if (lblReplyTo != null) {
      lblReplyTo.setVisible(false);
    }
  }

  // =====================================================================
  // ФАЙЛИ
  // =====================================================================

  @FXML
  private void handleUploadFile() {
    File file = dialogs.showFileChooser(lblTeamName.getScene().getWindow());
    if (file == null) return;

    Long teamId = viewModel.teamProperty().get().id();
    Long userId = session.getCurrentUserId();

    Task<FileDto> task =
        new Task<>() {
          @Override
          protected FileDto call() throws Exception {
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) mimeType = "application/octet-stream";
            return fileService.saveFileMetadata(
                teamId, null, userId, file.getName(), file.length(), mimeType);
          }
        };

    task.setOnSucceeded(
        e -> {
          viewModel.getFiles().addFirst(task.getValue());
          dialogs.showInfo("Файл завантажено", "Файл «" + file.getName() + "» додано до команди.");
        });
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  private void handleDeleteFile() {
    FileDto selected = listFiles.getSelectionModel().getSelectedItem();
    if (selected == null) return;

    if (!dialogs.showConfirmation("Видалення файлу", "Видалити «" + selected.fileName() + "»?")) {
      return;
    }

    Long userId = session.getCurrentUserId();
    Task<Void> task =
        new Task<>() {
          @Override
          protected Void call() {
            fileService.deleteFile(selected.id(), userId);
            return null;
          }
        };
    task.setOnSucceeded(e -> viewModel.getFiles().remove(selected));
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  // =====================================================================
  // УЧАСНИКИ
  // =====================================================================

  @FXML
  private void handleAddMember() {
    String[] data = dialogs.showAddMemberDialog();
    if (data == null) return;

    String username = data[0];
    TeamRole role = TeamRole.valueOf(data[1]);
    Long teamId = viewModel.teamProperty().get().id();

    Task<TeamMemberDto> task =
        new Task<>() {
          @Override
          protected TeamMemberDto call() {
            UserDto user = userService.getByUsername(username);
            return teamService.addMember(teamId, user.id(), role, session.getCurrentUserId());
          }
        };

    task.setOnSucceeded(
        e -> {
          viewModel.getMembers().add(task.getValue());
          dialogs.showInfo(
              "Учасника додано", "Користувача @" + username + " додано з роллю " + role);
        });
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  private void handleRemoveMember() {
    TeamMemberDto selected = listMembers.getSelectionModel().getSelectedItem();
    if (selected == null) return;

    boolean confirmed =
        dialogs.showConfirmation(
            "Видалення учасника", "Видалити " + selected.fullName() + " з команди?");
    if (!confirmed) return;

    Long teamId = viewModel.teamProperty().get().id();
    Task<Void> task =
        new Task<>() {
          @Override
          protected Void call() {
            teamService.removeMember(teamId, selected.userId(), session.getCurrentUserId());
            return null;
          }
        };

    task.setOnSucceeded(
        e -> viewModel.getMembers().removeIf(m -> m.userId().equals(selected.userId())));
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  // =====================================================================
  // НАВІГАЦІЯ
  // =====================================================================

  @FXML
  private void handleBack() {
    navigation.showDashboard();
  }

  @FXML
  private void handleRefresh() {
    loadAll();
  }

  // =====================================================================
  // ДОПОМІЖНІ МЕТОДИ
  // =====================================================================

  private void setupTaskCells(ListView<TaskDto> list) {
    list.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(TaskDto t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) {
                  setText(null);
                  setStyle("");
                  return;
                }

                String pIcon =
                    switch (t.priority()) {
                      case CRITICAL -> "C ";
                      case HIGH -> "H ";
                      case MEDIUM -> "M ";
                      case LOW -> "L ";
                    };
                String assignees =
                    t.assigneeNames().isEmpty()
                        ? ""
                        : " [" + String.join(", ", t.assigneeNames()) + "]";
                String badges = "";
                if (t.commentCount() > 0) badges += " 💬" + t.commentCount();
                if (t.fileCount() > 0) badges += " 📎" + t.fileCount();

                setText(pIcon + t.title() + assignees + badges);
                setStyle(t.overdue() ? "-fx-text-fill: #c0392b;" : "");
              }
            });
  }

  private void addTaskDoubleClick(ListView<TaskDto> list) {
    list.setOnMouseClicked(
        e -> {
          if (e.getClickCount() == 2) {
            TaskDto selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
              dialogs.showTaskDetail(
                  selected.id(), viewModel.teamProperty().get().id(), list.getScene().getWindow());
              loadAll(); // оновити після закриття діалогу
            }
          }
        });
  }

  private void setupStatusMenu(ListView<TaskDto> list, TaskStatus current) {
    ContextMenu menu = new ContextMenu();
    for (TaskStatus target : TaskStatus.values()) {
      if (target == current) continue;
      MenuItem item = new MenuItem("→ " + formatStatus(target));
      item.setOnAction(
          e -> {
            TaskDto sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            var task = viewModel.changeStatusAction(sel.id(), target);
            task.setOnSucceeded(ev -> viewModel.onStatusChanged(task.getValue()));
            task.setOnFailed(ev -> dialogs.showError("Помилка", task.getException().getMessage()));
            new Thread(task).start();
          });
      menu.getItems().add(item);
    }

    // Видалити задачу
    menu.getItems().add(new SeparatorMenuItem());
    MenuItem deleteItem = new MenuItem("Видалити задачу");
    deleteItem.setStyle("-fx-text-fill: #c0392b;");
    deleteItem.setOnAction(
        e -> {
          TaskDto sel = list.getSelectionModel().getSelectedItem();
          if (sel == null) return;
          if (!dialogs.showConfirmation("Видалення", "Видалити задачу «" + sel.title() + "»?")) {
            return;
          }
          var deleteTask = viewModel.deleteTaskAction(sel.id());
          deleteTask.setOnSucceeded(ev -> viewModel.onTaskDeleted(sel.id()));
          deleteTask.setOnFailed(
              ev -> dialogs.showError("Помилка видалення", deleteTask.getException().getMessage()));
          new Thread(deleteTask).start();
        });
    menu.getItems().add(deleteItem);

    list.setContextMenu(menu);
  }

  private String formatStatus(TaskStatus s) {
    return switch (s) {
      case TODO -> "TODO";
      case IN_PROGRESS -> "В роботі";
      case DONE -> "Виконано";
    };
  }
}

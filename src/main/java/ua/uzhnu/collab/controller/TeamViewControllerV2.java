package ua.uzhnu.collab.controller;

import java.io.File;
import java.nio.file.Files;
import java.util.function.Supplier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
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
import ua.uzhnu.collab.service.ReportService;
import ua.uzhnu.collab.service.TeamService;
import ua.uzhnu.collab.service.UserService;
import ua.uzhnu.collab.viewmodel.TeamViewModel;
import ua.uzhnu.collab.ui.CalendarViewHelper;
import ua.uzhnu.collab.ui.CalendarViewHelper.CalendarMode;
import ua.uzhnu.collab.ui.CalendarViewHelper.DateRange;

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
  private final ReportService reportService;
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
  @FXML private TextField txtFileSearch;
  @FXML private DatePicker dpCalendar;
  @FXML private ToggleGroup calendarModeGroup;
  @FXML private ToggleButton btnCalendarMonth;
  @FXML private ToggleButton btnCalendarWeek;
  @FXML private ToggleButton btnCalendarList;
  @FXML private ScrollPane spCalendarMonth;
  @FXML private ScrollPane spCalendarWeek;
  @FXML private GridPane paneCalendarMonth;
  @FXML private GridPane paneCalendarWeek;
  @FXML private Label lblCalendarRange;
  @FXML private ListView<TaskDto> listCalendarTasks;
  @FXML private ListView<TeamMemberDto> listMembers;

  @FXML private HBox analyticsSummaryBox;
  @FXML private VBox analyticsStatusBox;
  @FXML private Label lblAnalyticsUpdatedAt;
  @FXML private BarChart<String, Number> chartWorkload;
  @FXML private TableView<UserWorkloadDto> tableWorkload;
  @FXML private TableColumn<UserWorkloadDto, String> colMemberName;
  @FXML private TableColumn<UserWorkloadDto, Number> colTodo;
  @FXML private TableColumn<UserWorkloadDto, Number> colInProgress;
  @FXML private TableColumn<UserWorkloadDto, Number> colDone;
  @FXML private TableColumn<UserWorkloadDto, Number> colOverdue;

  private CalendarMode calendarMode = CalendarMode.MONTH;
  private LocalDate calendarAnchorDate = LocalDate.now();

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
                  reply.getStyleClass().add("chat-message-reply");
                  box.getChildren().add(reply);
                }
                Label content =
                    new Label(
                        msg.createdAt().format(TIME_FMT)
                            + "  "
                            + msg.authorName()
                            + ": "
                            + msg.content());
                content.getStyleClass().add("chat-message-content");
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

    // Календар: місяць / тиждень / список
    initCalendarView();

    // Аналітика продуктивності
    initAnalyticsView();

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
                  getStyleClass().add("member-owner");
                } else {
                  getStyleClass().remove("member-owner");
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
    if (txtFileSearch != null) {
      txtFileSearch.setOnAction(e -> handleSearchFiles());
    }

    // Ініціалізація reply-мітки
    if (lblReplyTo != null) {
      lblReplyTo.setVisible(false);
      lblReplyTo.setOnMouseClicked(e -> cancelReply());
    }

    loadAll();

    if (dpCalendar != null) {
      dpCalendar.setValue(LocalDate.now());
    }
  }

  // =====================================================================
  // ЗАВАНТАЖЕННЯ
  // =====================================================================

  private void initCalendarView() {
    if (listCalendarTasks != null) {
      listCalendarTasks.setItems(viewModel.getCalendarTasks());
      CalendarViewHelper.installAgendaCellFactory(
          listCalendarTasks,
          t -> formatAgendaItem(t, false),
          this::openTaskDetailFromCalendar);
    }

    if (calendarModeGroup != null) {
      calendarModeGroup
          .selectedToggleProperty()
          .addListener(
              (obs, oldToggle, newToggle) -> {
                if (newToggle == btnCalendarWeek) {
                  calendarMode = CalendarMode.WEEK;
                } else if (newToggle == btnCalendarList) {
                  calendarMode = CalendarMode.LIST;
                } else {
                  calendarMode = CalendarMode.MONTH;
                }
                updateCalendarVisibility();
                reloadCalendarView();
              });
    }

    if (dpCalendar != null) {
      dpCalendar
          .valueProperty()
          .addListener(
              (obs, oldValue, newValue) -> {
                if (newValue != null) {
                  calendarAnchorDate = newValue;
                  reloadCalendarView();
                }
              });
    }

    calendarMode = CalendarMode.MONTH;
    updateCalendarVisibility();
  }

  private void updateCalendarVisibility() {
    if (spCalendarMonth != null) {
      spCalendarMonth.setVisible(calendarMode == CalendarMode.MONTH);
      spCalendarMonth.setManaged(calendarMode == CalendarMode.MONTH);
    }
    if (spCalendarWeek != null) {
      spCalendarWeek.setVisible(calendarMode == CalendarMode.WEEK);
      spCalendarWeek.setManaged(calendarMode == CalendarMode.WEEK);
    }
    if (listCalendarTasks != null) {
      listCalendarTasks.setVisible(calendarMode == CalendarMode.LIST);
      listCalendarTasks.setManaged(calendarMode == CalendarMode.LIST);
    }
  }

  private void reloadCalendarView() {
    if (dpCalendar != null && dpCalendar.getValue() != null) {
      calendarAnchorDate = dpCalendar.getValue();
    }
    DateRange range = CalendarViewHelper.rangeFor(calendarAnchorDate, calendarMode);
    if (lblCalendarRange != null) {
      lblCalendarRange.setText(CalendarViewHelper.formatRange(range));
    }

    var task = viewModel.loadTasksForRangeAction(range.from(), range.to());
    task.setOnSucceeded(
        e -> {
          viewModel.getCalendarTasks().setAll(CalendarViewHelper.normalizedTasks(task.getValue()));
          renderCalendarView();
        });
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    Thread t = new Thread(task, "team-calendar-load");
    t.setDaemon(true);
    t.start();
  }

  private void renderCalendarView() {
    DateRange range = CalendarViewHelper.rangeFor(calendarAnchorDate, calendarMode);
    List<TaskDto> tasks = viewModel.getCalendarTasks();
    switch (calendarMode) {
      case MONTH ->
          CalendarViewHelper.renderMonth(
              paneCalendarMonth,
              calendarAnchorDate,
              tasks,
              t -> formatCalendarChip(t, false),
              this::openTaskDetailFromCalendar,
              this::selectCalendarDate);
      case WEEK ->
          CalendarViewHelper.renderWeek(
              paneCalendarWeek,
              calendarAnchorDate,
              tasks,
              t -> formatCalendarChip(t, false),
              this::openTaskDetailFromCalendar,
              this::selectCalendarDate);
      case LIST -> {
        if (listCalendarTasks != null) {
          viewModel.getCalendarTasks().setAll(CalendarViewHelper.normalizedTasks(tasks));
        }
      }
    }
    if (lblCalendarRange != null) {
      lblCalendarRange.setText(CalendarViewHelper.formatRange(range));
    }
  }

  private void selectCalendarDate(LocalDate date) {
    if (date != null && dpCalendar != null) {
      dpCalendar.setValue(date);
    }
  }

  private void initAnalyticsView() {
    if (tableWorkload != null) {
      tableWorkload.setItems(viewModel.getMemberWorkloads());
      if (colMemberName != null) {
        colMemberName.setCellValueFactory(
            data -> new javafx.beans.property.SimpleStringProperty(data.getValue().fullName()));
      }
      if (colTodo != null) {
        colTodo.setCellValueFactory(
            data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().todoCount()));
      }
      if (colInProgress != null) {
        colInProgress.setCellValueFactory(
            data ->
                new javafx.beans.property.SimpleIntegerProperty(data.getValue().inProgressCount()));
      }
      if (colDone != null) {
        colDone.setCellValueFactory(
            data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().doneCount()));
      }
      if (colOverdue != null) {
        colOverdue.setCellValueFactory(
            data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().overdueCount()));
      }
    }
    refreshAnalyticsView();
  }

  private void refreshAnalyticsView() {
    TeamStatsDto stats = viewModel.statsProperty().get();
    if (stats == null) {
      return;
    }

    renderAnalyticsSummary(stats);
    renderStatusBreakdown(stats);
    renderWorkloadChart(viewModel.getMemberWorkloads());

    if (lblAnalyticsUpdatedAt != null) {
      lblAnalyticsUpdatedAt.setText(
          "Оновлено: "
              + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
    }
  }

  private void renderAnalyticsSummary(TeamStatsDto stats) {
    if (analyticsSummaryBox == null) {
      return;
    }
    int total = Math.max(0, stats.totalTasks());
    analyticsSummaryBox.getChildren().setAll(
        buildMetricCard("Учасники", String.valueOf(stats.memberCount()), "team-analytics-card"),
        buildMetricCard("Задачі", String.valueOf(stats.totalTasks()), "team-analytics-card"),
        buildMetricCard("Виконано", percent(stats.doneCount(), total), "team-analytics-card-success"),
        buildMetricCard("Прострочено", percent(stats.overdueCount(), total), "team-analytics-card-danger"),
        buildMetricCard("Файли", String.valueOf(stats.fileCount()), "team-analytics-card"));
  }

  private void renderStatusBreakdown(TeamStatsDto stats) {
    if (analyticsStatusBox == null) {
      return;
    }
    int total = Math.max(0, stats.totalTasks());
    analyticsStatusBox.getChildren().setAll(
        buildStatusRow("TODO", stats.todoCount(), total),
        buildStatusRow("В роботі", stats.inProgressCount(), total),
        buildStatusRow("Виконано", stats.doneCount(), total),
        buildStatusRow("Прострочено", stats.overdueCount(), total));
  }

  private void renderWorkloadChart(List<UserWorkloadDto> workloads) {
    if (chartWorkload == null) {
      return;
    }
    chartWorkload.getData().clear();
    XYChart.Series<String, Number> series = new XYChart.Series<>();
    series.setName("Задачі на учасника");
    for (UserWorkloadDto workload : workloads) {
      int total =
          workload.todoCount()
              + workload.inProgressCount()
              + workload.doneCount()
              + workload.overdueCount();
      series.getData().add(new XYChart.Data<>(shortName(workload.fullName()), total));
    }
    chartWorkload.getData().add(series);
  }

  private VBox buildMetricCard(String title, String value, String styleClass) {
    VBox card = new VBox(4);
    card.getStyleClass().add(styleClass);
    Label lblTitle = new Label(title);
    lblTitle.getStyleClass().add("team-analytics-card-title");
    Label lblValue = new Label(value);
    lblValue.getStyleClass().add("team-analytics-card-value");
    card.getChildren().addAll(lblTitle, lblValue);
    return card;
  }

  private HBox buildStatusRow(String title, int value, int total) {
    HBox row = new HBox(8);
    row.getStyleClass().add("team-analytics-row");
    Label lblTitle = new Label(title);
    lblTitle.setMinWidth(110);
    Label lblValue = new Label(String.valueOf(value));
    lblValue.getStyleClass().add("team-analytics-row-value");
    Label lblPct = new Label(percent(value, total));
    lblPct.getStyleClass().add("team-analytics-row-pct");
    row.getChildren().addAll(lblTitle, lblValue, lblPct);
    return row;
  }

  private String percent(int value, int total) {
    if (total <= 0) {
      return "0%";
    }
    return String.format("%d%%", Math.round(value * 100.0 / total));
  }

  private String shortName(String name) {
    if (name == null) return "";
    return name.length() <= 14 ? name : name.substring(0, 11) + "…";
  }

  private void setCalendarAnchorDate(LocalDate date) {
    if (date == null) {
      return;
    }
    calendarAnchorDate = date;
    if (dpCalendar == null || date.equals(dpCalendar.getValue())) {
      reloadCalendarView();
    } else {
      dpCalendar.setValue(date);
    }
  }

  private void openTaskDetailFromCalendar(TaskDto task) {
    if (task == null || listCalendarTasks == null || listCalendarTasks.getScene() == null) {
      return;
    }
    dialogs.showTaskDetail(task.id(), task.teamId(), listCalendarTasks.getScene().getWindow());
    reloadCalendarView();
  }

  private String formatCalendarChip(TaskDto task, boolean agenda) {
    String time = task.dueDate() != null ? task.dueDate().toLocalTime().toString() : "";
    String prefix = time.isEmpty() ? "" : time + " · ";
    return agenda ? prefix + task.title() : prefix + task.title();
  }

  private String formatAgendaItem(TaskDto task, boolean includeTeam) {
    StringBuilder sb = new StringBuilder();
    if (task.dueDate() != null) {
      sb.append(task.dueDate().toLocalDate()).append(" ").append(task.dueDate().toLocalTime()).append(" — ");
    }
    sb.append(task.title());
    if (includeTeam && task.teamName() != null && !task.teamName().isBlank()) {
      sb.append(" (").append(task.teamName()).append(")");
    }
    return sb.toString();
  }

  private void loadAll() {
    viewModel.loadingProperty().set(true);
    var task = viewModel.createLoadAllTask();
    task.setOnSucceeded(
        e -> {
          viewModel.loadingProperty().set(false);
          reloadCalendarView();
          Platform.runLater(this::refreshAnalyticsView);
        });
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
                  task.setOnSucceeded(
                      e -> {
                        viewModel.onTaskCreated(task.getValue());
                        loadAll();
                      });
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

  @FXML
  private void handleSearchFiles() {
    String q = txtFileSearch.getText().trim();
    if (q.isEmpty()) {
      loadAll();
      return;
    }
    var task = viewModel.searchFilesAction(q);
    task.setOnSucceeded(
        e -> {
          List<FileDto> results = task.getValue();
          viewModel.getFiles().setAll(results);
        });
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
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
          viewModel.getFiles().add(0, task.getValue());
          dialogs.showInfo("Файл завантажено", "Файл «" + file.getName() + "» додано до команди.");
          refreshAnalyticsView();
        });
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    new Thread(task).start();
  }

  @FXML
  private void handleCalendarToday() {
    setCalendarAnchorDate(LocalDate.now());
  }

  @FXML
  private void handleCalendarPrevious() {
    setCalendarAnchorDate(CalendarViewHelper.shiftAnchor(calendarAnchorDate, calendarMode, -1));
  }

  @FXML
  private void handleCalendarNext() {
    setCalendarAnchorDate(CalendarViewHelper.shiftAnchor(calendarAnchorDate, calendarMode, 1));
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
          loadAll();
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

    task.setOnSucceeded(e -> loadAll());
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
            task.setOnSucceeded(
                ev -> {
                  viewModel.onStatusChanged(task.getValue());
                  loadAll();
                });
            task.setOnFailed(ev -> dialogs.showError("Помилка", task.getException().getMessage()));
            new Thread(task).start();
          });
      menu.getItems().add(item);
    }

    // Видалити задачу
    menu.getItems().add(new SeparatorMenuItem());
    MenuItem deleteItem = new MenuItem("Видалити задачу");
    deleteItem.getStyleClass().add("danger-menu-item");
    deleteItem.setOnAction(
        e -> {
          TaskDto sel = list.getSelectionModel().getSelectedItem();
          if (sel == null) return;
          if (!dialogs.showConfirmation("Видалення", "Видалити задачу «" + sel.title() + "»?")) {
            return;
          }
          var deleteTask = viewModel.deleteTaskAction(sel.id());
          deleteTask.setOnSucceeded(
              ev -> {
                viewModel.onTaskDeleted(sel.id());
                loadAll();
              });
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

  // =====================================================================
  // EXPORT HANDLERS
  // =====================================================================

  @FXML private void handleExportTasksPdf() {
    runExport(() -> reportService.exportTasksPdf(viewModel.teamProperty().get().id()), "tasks-report", "pdf");
  }

  @FXML private void handleExportTasksExcel() {
    runExport(() -> reportService.exportTasksExcel(viewModel.teamProperty().get().id()), "tasks-report", "xlsx");
  }

  @FXML private void handleExportStatsPdf() {
    runExport(() -> reportService.exportStatsPdf(viewModel.teamProperty().get().id()), "stats-report", "pdf");
  }

  @FXML private void handleExportStatsExcel() {
    runExport(() -> reportService.exportStatsExcel(viewModel.teamProperty().get().id()), "stats-report", "xlsx");
  }

  @FXML private void handleExportWorkloadPdf() {
    runExport(() -> reportService.exportWorkloadPdf(viewModel.teamProperty().get().id()), "workload-report", "pdf");
  }

  @FXML private void handleExportWorkloadExcel() {
    runExport(() -> reportService.exportWorkloadExcel(viewModel.teamProperty().get().id()), "workload-report", "xlsx");
  }

  private void runExport(Supplier<byte[]> generator, String defaultName, String ext) {
    viewModel.loadingProperty().set(true);
    Task<byte[]> task = new Task<>() {
      @Override protected byte[] call() { return generator.get(); }
    };
    task.setOnSucceeded(e -> {
      viewModel.loadingProperty().set(false);
      File file = dialogs.showSaveFileDialog(lblTeamName.getScene().getWindow(), defaultName, ext);
      if (file == null) return;
      try {
        Files.write(file.toPath(), task.getValue());
        dialogs.showInfo("Збережено", "Файл збережено: " + file.getName());
      } catch (Exception ex) {
        dialogs.showError("Помилка запису", ex.getMessage());
      }
    });
    task.setOnFailed(e -> {
      viewModel.loadingProperty().set(false);
      dialogs.showError("Помилка експорту", task.getException().getMessage());
    });
    Thread t = new Thread(task, "export-" + ext);
    t.setDaemon(true);
    t.start();
  }
}

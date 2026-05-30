package ua.uzhnu.collab.controller;

import java.util.Optional;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.NavigationService;
import ua.uzhnu.collab.config.PreferencesService;
import ua.uzhnu.collab.config.SessionContext;
import ua.uzhnu.collab.dto.Dtos.TeamDto;
import ua.uzhnu.collab.viewmodel.DashboardViewModel;
import ua.uzhnu.collab.viewmodel.TeamViewModel;
import ua.uzhnu.collab.service.TaskService;
import ua.uzhnu.collab.dto.Dtos.TaskDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import ua.uzhnu.collab.ui.CalendarViewHelper;
import ua.uzhnu.collab.ui.CalendarViewHelper.CalendarMode;
import ua.uzhnu.collab.ui.CalendarViewHelper.DateRange;

/**
 * FXML-контролер головного дашборду. Відображає список команд користувача та дозволяє створювати
 * нові.
 */
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardViewModel viewModel;
  private final TeamViewModel teamViewModel;
  private final NavigationService navigation;
  private final SessionContext sessionContext;
  private final PreferencesService preferencesService;
  private final DialogHelper dialogs;
  private final TaskService taskService;

  @FXML private Label lblWelcome;
  @FXML private ListView<TeamDto> listTeams;
  @FXML private ProgressIndicator spinner;
  @FXML private Label lblTeamCount;
  @FXML private DatePicker dpGlobalCalendar;
  @FXML private ToggleGroup calendarModeGroup;
  @FXML private ToggleButton btnCalendarMonth;
  @FXML private ToggleButton btnCalendarWeek;
  @FXML private ToggleButton btnCalendarList;
  @FXML private ScrollPane spCalendarMonth;
  @FXML private ScrollPane spCalendarWeek;
  @FXML private GridPane paneCalendarMonth;
  @FXML private GridPane paneCalendarWeek;
  @FXML private Label lblCalendarRange;
  @FXML private ListView<TaskDto> listCalendarGlobal;

  private CalendarMode calendarMode = CalendarMode.MONTH;
  private LocalDate calendarAnchorDate = LocalDate.now();

  @FXML
  public void initialize() {
    lblWelcome.textProperty().bind(viewModel.welcomeMessageProperty());
    spinner.visibleProperty().bind(viewModel.loadingProperty());
    listTeams.setItems(viewModel.getTeams());

    // Кастомне відображення елемента списку
    listTeams.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(TeamDto team, boolean empty) {
                super.updateItem(team, empty);
                if (empty || team == null) {
                  setText(null);
                } else {
                  setText(
                      team.name()
                          + " — "
                          + team.memberCount()
                          + " уч., "
                          + team.taskCount()
                          + " задач");
                }
              }
            });

    // Контекстне меню: вийти / видалити команду
    ContextMenu ctxMenu = new ContextMenu();
    MenuItem miLeave = new MenuItem("Покинути команду");
    MenuItem miDelete = new MenuItem("Видалити команду");
    ctxMenu.getItems().addAll(miLeave, miDelete);

    miLeave.setOnAction(e -> {
      TeamDto selected = listTeams.getSelectionModel().getSelectedItem();
      if (selected == null) return;
      boolean confirmed = dialogs.showConfirmation(
          "Покинути команду",
          "Ви впевнені, що хочете покинути команду \"" + selected.name() + "\"?");
      if (!confirmed) return;
      var task = viewModel.leaveTeamTask(selected.id());
      task.setOnSucceeded(ev -> viewModel.getTeams().remove(selected));
      task.setOnFailed(ev -> dialogs.showError("Помилка", task.getException().getMessage()));
      new Thread(task).start();
    });

    miDelete.setOnAction(e -> {
      TeamDto selected = listTeams.getSelectionModel().getSelectedItem();
      if (selected == null) return;
      boolean confirmed = dialogs.showConfirmation(
          "Видалити команду",
          "Видалити команду \"" + selected.name() + "\"? Усі задачі, файли та чат будуть втрачені.");
      if (!confirmed) return;
      var task = viewModel.deleteTeamTask(selected.id());
      task.setOnSucceeded(ev -> viewModel.getTeams().remove(selected));
      task.setOnFailed(ev -> dialogs.showError("Помилка", task.getException().getMessage()));
      new Thread(task).start();
    });

    listTeams.setContextMenu(ctxMenu);

    // Подвійний клік — відкрити команду
    listTeams.setOnMouseClicked(
        e -> {
          if (e.getClickCount() == 2) {
            handleOpenTeam();
          }
        });

    // Слухач на зміну кількості команд
    viewModel
        .getTeams()
        .addListener(
            (javafx.collections.ListChangeListener<TeamDto>)
                c -> lblTeamCount.setText("Команд: " + viewModel.getTeams().size()));

    // Завантажити команди
    loadTeams();

    initCalendarView();

    if (dpGlobalCalendar != null) {
      dpGlobalCalendar.setValue(LocalDate.now());
    }
  }

  private void loadTeams() {
    viewModel.loadingProperty().set(true);
    var task = viewModel.createLoadTeamsTask();
    task.setOnSucceeded(
        e -> {
          viewModel.loadingProperty().set(false);
          viewModel.onTeamsLoaded(task.getValue());
        });
    task.setOnFailed(
        e -> {
          viewModel.loadingProperty().set(false);
          dialogs.showError("Помилка завантаження команд", task.getException().getMessage());
        });
    new Thread(task).start();
  }

  private void initCalendarView() {
    if (listCalendarGlobal != null) {
      listCalendarGlobal.setItems(viewModel.getCalendarTasks());
      CalendarViewHelper.installAgendaCellFactory(
          listCalendarGlobal,
          t -> formatAgendaItem(t),
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
                reloadGlobalCalendarView();
              });
    }

    if (dpGlobalCalendar != null) {
      dpGlobalCalendar
          .valueProperty()
          .addListener(
              (obs, oldValue, newValue) -> {
                if (newValue != null) {
                  calendarAnchorDate = newValue;
                  reloadGlobalCalendarView();
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
    if (listCalendarGlobal != null) {
      listCalendarGlobal.setVisible(calendarMode == CalendarMode.LIST);
      listCalendarGlobal.setManaged(calendarMode == CalendarMode.LIST);
    }
  }

  private void reloadGlobalCalendarView() {
    if (dpGlobalCalendar != null && dpGlobalCalendar.getValue() != null) {
      calendarAnchorDate = dpGlobalCalendar.getValue();
    }
    DateRange range = CalendarViewHelper.rangeFor(calendarAnchorDate, calendarMode);
    if (lblCalendarRange != null) {
      lblCalendarRange.setText(CalendarViewHelper.formatRange(range));
    }

    Task<List<TaskDto>> task = loadGlobalCalendarTask(range.from(), range.to());
    task.setOnSucceeded(
        e -> {
          viewModel.getCalendarTasks().setAll(CalendarViewHelper.normalizedTasks(task.getValue()));
          renderGlobalCalendarView();
        });
    task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
    Thread t = new Thread(task, "global-calendar-load");
    t.setDaemon(true);
    t.start();
  }

  private Task<List<TaskDto>> loadGlobalCalendarTask(LocalDate fromDate, LocalDate toDate) {
    LocalDateTime from = fromDate.atStartOfDay();
    LocalDateTime to = toDate.atTime(23, 59, 59);
    return new javafx.concurrent.Task<>() {
      @Override
      protected List<TaskDto> call() {
        return taskService.getUpcomingGlobalBetween(from, to);
      }
    };
  }

  private void renderGlobalCalendarView() {
    DateRange range = CalendarViewHelper.rangeFor(calendarAnchorDate, calendarMode);
    List<TaskDto> tasks = viewModel.getCalendarTasks();
    switch (calendarMode) {
      case MONTH ->
          CalendarViewHelper.renderMonth(
              paneCalendarMonth,
              calendarAnchorDate,
              tasks,
              this::formatCalendarChip,
              this::openTaskDetailFromCalendar,
              this::selectCalendarDate);
      case WEEK ->
          CalendarViewHelper.renderWeek(
              paneCalendarWeek,
              calendarAnchorDate,
              tasks,
              this::formatCalendarChip,
              this::openTaskDetailFromCalendar,
              this::selectCalendarDate);
      case LIST -> {
        // agenda list already bound to the shared observable list
      }
    }
    if (lblCalendarRange != null) {
      lblCalendarRange.setText(CalendarViewHelper.formatRange(range));
    }
  }

  private void selectCalendarDate(LocalDate date) {
    if (date != null && dpGlobalCalendar != null) {
      dpGlobalCalendar.setValue(date);
    }
  }

  private void setCalendarAnchorDate(LocalDate date) {
    if (date == null) {
      return;
    }
    calendarAnchorDate = date;
    if (dpGlobalCalendar == null || date.equals(dpGlobalCalendar.getValue())) {
      reloadGlobalCalendarView();
    } else {
      dpGlobalCalendar.setValue(date);
    }
  }

  private void openTaskDetailFromCalendar(TaskDto task) {
    if (task == null || listCalendarGlobal == null || listCalendarGlobal.getScene() == null) {
      return;
    }
    dialogs.showTaskDetail(task.id(), task.teamId(), listCalendarGlobal.getScene().getWindow());
    reloadGlobalCalendarView();
  }

  private String formatCalendarChip(TaskDto task) {
    StringBuilder sb = new StringBuilder();
    if (task.dueDate() != null) {
      sb.append(task.dueDate().toLocalTime()).append(" · ");
    }
    sb.append(task.title());
    if (task.teamName() != null && !task.teamName().isBlank()) {
      sb.append(" · ").append(task.teamName());
    }
    return sb.toString();
  }

  private String formatAgendaItem(TaskDto task) {
    StringBuilder sb = new StringBuilder();
    if (task.dueDate() != null) {
      sb.append(task.dueDate().toLocalDate()).append(" ").append(task.dueDate().toLocalTime()).append(" — ");
    }
    sb.append(task.title());
    if (task.teamName() != null && !task.teamName().isBlank()) {
      sb.append(" (").append(task.teamName()).append(")");
    }
    return sb.toString();
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

  @FXML
  private void handleCreateTeam() {
    // Діалог створення команди
    Dialog<String[]> dialog = new Dialog<>();
    dialog.setTitle("Створити команду");
    dialog.setHeaderText("Нова команда");

    ButtonType createBtn = new ButtonType("Створити", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

    TextField txtName = new TextField();
    txtName.setPromptText("Назва команди");
    TextArea txtDesc = new TextArea();
    txtDesc.setPromptText("Опис (необов'язково)");
    txtDesc.setPrefRowCount(3);

    VBox content = new VBox(10, new Label("Назва:"), txtName, new Label("Опис:"), txtDesc);
    dialog.getDialogPane().setContent(content);

    dialog.setResultConverter(
        btn -> {
          if (btn == createBtn) {
            return new String[] {txtName.getText(), txtDesc.getText()};
          }
          return null;
        });

    Optional<String[]> result = dialog.showAndWait();
    result.ifPresent(
        data -> {
          if (data[0] == null || data[0].isBlank()) {
            dialogs.showError("Помилка", "Назва команди не може бути порожньою");
            return;
          }
          var task = viewModel.createTeamTask(data[0], data[1]);
          task.setOnSucceeded(e -> viewModel.onTeamCreated(task.getValue()));
          task.setOnFailed(e -> dialogs.showError("Помилка", task.getException().getMessage()));
          new Thread(task).start();
        });
  }

  @FXML
  private void handleOpenTeam() {
    TeamDto selected = listTeams.getSelectionModel().getSelectedItem();
    if (selected == null) return;

    teamViewModel.setTeamId(selected.id());
    navigation.showTeamView();
  }

  @FXML
  private void handleLogout() {
    preferencesService.clearCredentials();
    sessionContext.logout();
    navigation.showLogin();
  }

  @FXML
  private void handleRefresh() {
    loadTeams();
  }

  @FXML
  private void showSetting() {
    // Перейти на екран налаштувань
    navigation.showSettings();
  }

  @FXML
  private void showAccaunt() {
    // Перейти на екран профілю/акаунту
    navigation.showAccount();
  }
}

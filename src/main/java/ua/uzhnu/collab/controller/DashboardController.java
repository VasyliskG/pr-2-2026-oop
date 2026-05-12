package ua.uzhnu.collab.controller;

import java.util.Optional;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.NavigationService;
import ua.uzhnu.collab.config.SessionContext;
import ua.uzhnu.collab.dto.Dtos.TeamDto;
import ua.uzhnu.collab.viewmodel.DashboardViewModel;
import ua.uzhnu.collab.viewmodel.TeamViewModel;

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
  private final DialogHelper dialogs;

  @FXML private Label lblWelcome;
  @FXML private ListView<TeamDto> listTeams;
  @FXML private ProgressIndicator spinner;
  @FXML private Label lblTeamCount;

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
    sessionContext.logout();
    navigation.showLogin();
  }

  @FXML
  private void handleRefresh() {
    loadTeams();
  }
}

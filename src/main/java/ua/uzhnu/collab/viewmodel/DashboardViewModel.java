package ua.uzhnu.collab.viewmodel;

import java.util.List;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.SessionContext;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.service.TeamService;

/**
 * ViewModel для головного дашборду — списку команд користувача.
 *
 * <p>Scope = prototype: новий екземпляр при кожному відкритті дашборду, щоб уникнути застарілих
 * даних.
 */
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class DashboardViewModel {

  private final TeamService teamService;
  private final SessionContext sessionContext;

  private final ObservableList<TeamDto> teams = FXCollections.observableArrayList();
  private final StringProperty welcomeMessage = new SimpleStringProperty("");
  private final BooleanProperty loading = new SimpleBooleanProperty(false);
  private final ObjectProperty<TeamDto> selectedTeam = new SimpleObjectProperty<>();

  // ---- Геттери властивостей ----

  public ObservableList<TeamDto> getTeams() {
    return teams;
  }

  public StringProperty welcomeMessageProperty() {
    return welcomeMessage;
  }

  public BooleanProperty loadingProperty() {
    return loading;
  }

  public ObjectProperty<TeamDto> selectedTeamProperty() {
    return selectedTeam;
  }

  // ---- Завантаження даних ----

  /** Створює асинхронну задачу завантаження команд користувача. */
  public Task<List<TeamDto>> createLoadTeamsTask() {
    Long userId = sessionContext.getCurrentUserId();
    welcomeMessage.set("Вітаємо, " + sessionContext.getCurrentUser().fullName());

    return new Task<>() {
      @Override
      protected List<TeamDto> call() {
        return teamService.getTeamsByUser(userId);
      }
    };
  }

  /** Оновлює список команд після завантаження. */
  public void onTeamsLoaded(List<TeamDto> loadedTeams) {
    teams.setAll(loadedTeams);
  }

  // ---- Створення команди ----

  /** Створює нову команду. */
  public Task<TeamDto> createTeamTask(String name, String description) {
    Long userId = sessionContext.getCurrentUserId();
    return new Task<>() {
      @Override
      protected TeamDto call() {
        return teamService.create(new TeamCreateDto(name, description), userId);
      }
    };
  }

  /** Додає новостворену команду до списку. */
  public void onTeamCreated(TeamDto team) {
    teams.add(team);
  }
}

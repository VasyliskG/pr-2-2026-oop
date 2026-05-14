package ua.uzhnu.collab.controller;

import java.io.File;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.SpringFxmlLoader;
import ua.uzhnu.collab.dto.Dtos.FileDto;
import ua.uzhnu.collab.enums.TeamRole;

/**
 * Фабрика діалогових вікон для повторного використання.
 *
 * <p>Інкапсулює логіку створення стандартних діалогів: додавання учасника, вибір файлу,
 * підтвердження дії, відображення помилки. Патерн: Factory Method.
 */
@Component
@RequiredArgsConstructor
public class DialogHelper {

  private final SpringFxmlLoader fxmlLoader;

  /** Відкриває діалог детального перегляду задачі як модальне вікно. */
  public void showTaskDetail(Long taskId, Long teamId, Window owner) {
    try {
      FXMLLoader loader = fxmlLoader.getLoader("/fxml/task_detail.fxml");
      Parent root = loader.load();

      TaskDetailController controller = loader.getController();
      controller.setTask(taskId, teamId);

      Stage dialog = new Stage();
      dialog.initModality(Modality.WINDOW_MODAL);
      dialog.initOwner(owner);
      dialog.setTitle("Деталі задачі");

      Scene scene = new Scene(root, 700, 600);
      scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

      dialog.setScene(scene);
      dialog.showAndWait();
    } catch (Exception e) {
      showError("Помилка відкриття задачі", e.getMessage());
    }
  }

  /**
   * Діалог додавання учасника до команди.
   *
   * @return масив [username, role] або null при скасуванні
   */
  public String[] showAddMemberDialog() {
    Dialog<String[]> dialog = new Dialog<>();
    dialog.setTitle("Додати учасника");
    dialog.setHeaderText("Введіть логін нового учасника");

    ButtonType addBtn = new ButtonType("Додати", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

    TextField txtUsername = new TextField();
    txtUsername.setPromptText("username");

    ComboBox<TeamRole> cbRole = new ComboBox<>();
    cbRole.getItems().addAll(TeamRole.MEMBER, TeamRole.ADMIN);
    cbRole.setValue(TeamRole.MEMBER);

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.add(new Label("Логін:"), 0, 0);
    grid.add(txtUsername, 1, 0);
    grid.add(new Label("Роль:"), 0, 1);
    grid.add(cbRole, 1, 1);

    dialog.getDialogPane().setContent(grid);

    dialog.setResultConverter(
        btn -> {
          if (btn == addBtn && !txtUsername.getText().isBlank()) {
            return new String[] {txtUsername.getText().trim(), cbRole.getValue().name()};
          }
          return null;
        });

    return dialog.showAndWait().orElse(null);
  }

  /** Діалог вибору файлу для завантаження. */
  public File showFileChooser(Window owner) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Вибрати файл для завантаження");
    chooser
        .getExtensionFilters()
        .addAll(
            new FileChooser.ExtensionFilter("Усі файли", "*.*"),
            new FileChooser.ExtensionFilter("Документи", "*.pdf", "*.docx", "*.xlsx"),
            new FileChooser.ExtensionFilter("Зображення", "*.png", "*.jpg", "*.svg"),
            new FileChooser.ExtensionFilter("Архіви", "*.zip", "*.tar.gz"),
            new FileChooser.ExtensionFilter("Вихідний код", "*.java", "*.sql", "*.xml"));
    return chooser.showOpenDialog(owner);
  }

  /**
   * Діалог збереження файлу.
   *
   * @param owner вікно-власник
   * @param defaultName ім'я файлу за замовчуванням
   * @param ext розширення без крапки, наприклад "pdf" або "xlsx"
   * @return обраний файл або null при скасуванні
   */
  public File showSaveFileDialog(Window owner, String defaultName, String ext) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Зберегти файл");
    chooser.setInitialFileName(defaultName);
    String description = ext.equalsIgnoreCase("pdf") ? "PDF документ" : "Excel таблиця";
    chooser
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter(description, "*." + ext.toLowerCase()));
    return chooser.showSaveDialog(owner);
  }

  /** Діалог підтвердження дії. */
  public boolean showConfirmation(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(title);
    alert.setContentText(message);
    Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && result.get() == ButtonType.OK;
  }

  /** Діалог помилки. */
  public void showError(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(title);
    alert.setContentText(message);
    alert.showAndWait();
  }

  /** Інформаційне повідомлення. */
  public void showInfo(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle(title);
    alert.setContentText(message);
    alert.showAndWait();
  }

  /** Форматує розмір файлу у людиночитаємий вигляд. */
  public String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " Б";
    if (bytes < 1024 * 1024) return String.format("%.1f КБ", bytes / 1024.0);
    return String.format("%.1f МБ", bytes / (1024.0 * 1024));
  }

  /** CellFactory для відображення файлів у ListView. */
  public Callback<ListView<FileDto>, ListCell<FileDto>> buildFileCellFactory() {
    return lv ->
        new ListCell<>() {
          @Override
          protected void updateItem(FileDto f, boolean empty) {
            super.updateItem(f, empty);
            if (empty || f == null) {
              setText(null);
              return;
            }
            String size = formatSize(f.fileSize());
            String task = f.taskTitle() != null ? " → " + f.taskTitle() : " (спільний)";
            setText(f.fileName() + " (" + size + ")" + task + " — " + f.uploaderName());
          }
        };
  }
}

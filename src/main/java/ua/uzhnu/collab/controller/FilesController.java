package ua.uzhnu.collab.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.io.File;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.ThemeManager;
import ua.uzhnu.collab.config.SessionContext;

@Component
@RequiredArgsConstructor
public class FilesController {

  private final SessionContext userSession;
  private final ThemeManager themeManager;

  @FXML private VBox rootPane;

  @FXML
  public void initialize() {}

  @FXML
  private void handleUpload() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Select file to upload");
    chooser.getExtensionFilters().addAll(
        new FileChooser.ExtensionFilter("All Files", "*.*"),
        new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.gif"),
        new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.docx", "*.xlsx")
    );
    File file = chooser.showOpenDialog(rootPane.getScene().getWindow());
    if (file != null) {
      showInfo("File Queued", "\"" + file.getName() + "\" queued for upload.\n(Requires DB connection to save.)");
    }
  }

  @FXML
  private void handleOpen(ActionEvent event) {
    Node src = (Node) event.getSource();
    String fileName = src.getId();
    showInfo("Open File", "\"" + fileName + "\"\n\nFile preview requires a connected storage backend.");
  }

  private void showInfo(String title, String content) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(content);
    alert.getDialogPane().getStylesheets().add(
        getClass().getResource(themeManager.getCurrentThemeCssFile()).toExternalForm());
    alert.showAndWait();
  }
}

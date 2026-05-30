package ua.uzhnu.collab.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.config.NavigationService;
import ua.uzhnu.collab.config.SessionContext;

@Component
@Scope("prototype")
@RequiredArgsConstructor
public class AccountController {

  private final NavigationService navigation;
  private final SessionContext sessionContext;

  @FXML private Label lblUsername;
  @FXML private Label lblFullName;
  @FXML private Label lblEmail;
  @FXML private Label lblCreatedAt;
  @FXML private Button btnBack;

  @FXML
  public void initialize() {
    if (sessionContext.isAuthenticated()) {
      var u = sessionContext.getCurrentUser();
      lblUsername.setText(u.username());
      lblFullName.setText(u.fullName());
      lblEmail.setText(u.email());
      lblCreatedAt.setText(u.createdAt().toString());
    } else {
      lblUsername.setText("(неавтентифікований)");
    }
  }

  @FXML
  private void handleBack() {
    navigation.showDashboard();
  }
}


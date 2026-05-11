package ua.uzhnu.collab;

import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/**
 * Зберігає посилання на головне вікно (Stage) застосунку. Використовується для навігації між
 * екранами.
 */
@Component
@Getter
@Setter
public class StageHolder {

  private Stage primaryStage;
}

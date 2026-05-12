package ua.uzhnu.collab;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входу в застосунок.
 *
 * <p>Запуск відбувається через JavaFX {@link Application#launch}, який делегує ініціалізацію
 * Spring-контексту класу {@link CollabFxApplication}.
 */
@SpringBootApplication
public class CollabApplication {

  public static void main(String[] args) {
    extractSslCert();
    Application.launch(CollabFxApplication.class, args);
  }

  // Extracts bundled Supabase CA cert from classpath to a temp file so the JDBC driver can load
  // it via a filesystem path. Skipped if app.ssl.cert is already set (e.g. via -D flag).
  private static void extractSslCert() {
    if (System.getProperty("app.ssl.cert") != null) return;
    try (InputStream in = CollabApplication.class.getResourceAsStream("/prod-ca-2021.crt")) {
      if (in == null) return;
      Path tmp = Files.createTempFile("prod-ca-2021-", ".crt");
      tmp.toFile().deleteOnExit();
      Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
      System.setProperty("app.ssl.cert", tmp.toString());
    } catch (IOException ignored) {
    }
  }
}

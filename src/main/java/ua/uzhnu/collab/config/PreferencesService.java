package ua.uzhnu.collab.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ua.uzhnu.collab.model.AppTheme;
import ua.uzhnu.collab.model.Credentials;

@Component
@Slf4j
public class PreferencesService {

  private static final String PREFS_DIR = ".collab";
  private static final String PREFS_FILE = "prefs.properties";
  private static final String USERNAME_KEY = "session.username.encrypted";
  private static final String PASSWORD_KEY = "session.password.encrypted";
  private static final String THEME_KEY = "theme";
  private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_TAG_LENGTH = 128;
  private static final int GCM_IV_LENGTH = 12;
  private static final String APP_SALT = "StudentCollabApp2026PreferencesSalt";
  private static final int PBKDF2_ITERATIONS = 65536;
  private static final int KEY_LENGTH = 256;

  private final Path prefsPath;
  private final SecureRandom random;

  public PreferencesService() {
    this.prefsPath =
        Paths.get(System.getProperty("user.home"), PREFS_DIR, PREFS_FILE);
    this.random = new SecureRandom();
  }

  public void saveCredentials(String username, String password) {
    try {
      ensurePrefsDir();
      Properties props = loadProperties();

      props.setProperty(USERNAME_KEY, encrypt(username));
      props.setProperty(PASSWORD_KEY, encrypt(password));

      saveProperties(props);
      log.info("Credentials saved to preferences");
    } catch (Exception e) {
      log.error("Failed to save credentials", e);
      throw new RuntimeException("Failed to save credentials", e);
    }
  }

  public Optional<Credentials> loadCredentials() {
    try {
      Properties props = loadProperties();

      String encryptedUsername = props.getProperty(USERNAME_KEY);
      String encryptedPassword = props.getProperty(PASSWORD_KEY);

      if (encryptedUsername == null || encryptedPassword == null) {
        return Optional.empty();
      }

      String username = decrypt(encryptedUsername);
      String password = decrypt(encryptedPassword);

      return Optional.of(new Credentials(username, password));
    } catch (Exception e) {
      log.warn("Failed to load credentials: {}", e.getMessage());
      return Optional.empty();
    }
  }

  public void clearCredentials() {
    try {
      ensurePrefsDir();
      Properties props = loadProperties();
      props.remove(USERNAME_KEY);
      props.remove(PASSWORD_KEY);
      saveProperties(props);
      log.info("Credentials cleared from preferences");
    } catch (Exception e) {
      log.error("Failed to clear credentials", e);
    }
  }

  public void saveTheme(AppTheme theme) {
    try {
      ensurePrefsDir();
      Properties props = loadProperties();
      props.setProperty(THEME_KEY, theme.name());
      saveProperties(props);
      log.info("Theme saved: {}", theme);
    } catch (Exception e) {
      log.error("Failed to save theme", e);
    }
  }

  public AppTheme loadTheme() {
    try {
      Properties props = loadProperties();
      String themeName = props.getProperty(THEME_KEY, AppTheme.DARK.name());
      return AppTheme.valueOf(themeName);
    } catch (Exception e) {
      log.warn("Failed to load theme, defaulting to DARK: {}", e.getMessage());
      return AppTheme.DARK;
    }
  }

  private Properties loadProperties() throws IOException {
    Properties props = new Properties();
    if (Files.exists(prefsPath)) {
      try (InputStream is = new FileInputStream(prefsPath.toFile())) {
        props.load(is);
      }
    }
    return props;
  }

  private void saveProperties(Properties props) throws IOException {
    try (OutputStream os = new FileOutputStream(prefsPath.toFile())) {
      props.store(os, "Student Collab Preferences");
    }

    try {
      Set<PosixFilePermission> permissions =
          PosixFilePermissions.fromString("rw-------");
      Files.setPosixFilePermissions(prefsPath, permissions);
    } catch (UnsupportedOperationException e) {
      log.debug("POSIX file permissions not supported on this platform");
    }
  }

  private void ensurePrefsDir() throws IOException {
    Path dir = prefsPath.getParent();
    if (!Files.exists(dir)) {
      try {
        Set<PosixFilePermission> permissions =
            PosixFilePermissions.fromString("rwx------");
        Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(permissions));
      } catch (UnsupportedOperationException e) {
        // Windows or filesystem without POSIX support
        Files.createDirectories(dir);
        log.warn("POSIX permissions not supported; relying on OS defaults");
      }
    }
  }

  private String encrypt(String value) throws Exception {
    byte[] salt = APP_SALT.getBytes(StandardCharsets.UTF_8);
    byte[] iv = new byte[GCM_IV_LENGTH];
    random.nextBytes(iv);

    javax.crypto.SecretKey key = deriveKey(salt);

    Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
    GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
    cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

    byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

    byte[] combined = new byte[iv.length + encrypted.length];
    System.arraycopy(iv, 0, combined, 0, iv.length);
    System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

    return java.util.Base64.getEncoder().encodeToString(combined);
  }

  private String decrypt(String encryptedBase64) throws Exception {
    byte[] combined = java.util.Base64.getDecoder().decode(encryptedBase64);

    if (combined.length < GCM_IV_LENGTH) {
      throw new IllegalArgumentException("Invalid encrypted data");
    }

    byte[] iv = new byte[GCM_IV_LENGTH];
    byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
    System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
    System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

    byte[] salt = APP_SALT.getBytes(StandardCharsets.UTF_8);
    javax.crypto.SecretKey key = deriveKey(salt);

    Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
    GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
    cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

    byte[] decrypted = cipher.doFinal(encrypted);
    return new String(decrypted, StandardCharsets.UTF_8);
  }

  private javax.crypto.SecretKey deriveKey(byte[] salt) throws Exception {
    String password = System.getProperty("user.name") + APP_SALT;
    KeySpec spec =
        new PBEKeySpec(
            password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    byte[] keyBytes = factory.generateSecret(spec).getEncoded();
    return new javax.crypto.spec.SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
  }
}

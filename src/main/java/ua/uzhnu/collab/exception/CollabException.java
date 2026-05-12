package ua.uzhnu.collab.exception;

/** Базове виключення платформи. Усі бізнес-виключення успадковують цей клас. */
public class CollabException extends RuntimeException {

  public CollabException(String message) {
    super(message);
  }

  public CollabException(String message, Throwable cause) {
    super(message, cause);
  }
}

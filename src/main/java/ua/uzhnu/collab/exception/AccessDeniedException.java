package ua.uzhnu.collab.exception;

/** Виключення: відмова в доступі (недостатня роль або не учасник команди). */
public class AccessDeniedException extends CollabException {

  public AccessDeniedException(String message) {
    super(message);
  }
}

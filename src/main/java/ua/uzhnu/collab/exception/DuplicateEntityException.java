package ua.uzhnu.collab.exception;

/** Виключення: спроба створити дублікат (порушення UNIQUE). */
public class DuplicateEntityException extends CollabException {

  public DuplicateEntityException(String message) {
    super(message);
  }
}

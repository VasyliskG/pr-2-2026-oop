package ua.uzhnu.collab.exception;

/** Виключення: сутність не знайдена за ідентифікатором. */
public class EntityNotFoundException extends CollabException {

  public EntityNotFoundException(String entityName, Long id) {
    super(entityName + " з ідентифікатором " + id + " не знайдено");
  }

  public EntityNotFoundException(String entityName, String field, String value) {
    super(entityName + " з " + field + " '" + value + "' не знайдено");
  }
}

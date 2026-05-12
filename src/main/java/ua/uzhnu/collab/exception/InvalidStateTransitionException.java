package ua.uzhnu.collab.exception;

import ua.uzhnu.collab.enums.TaskStatus;

/** Виключення: недопустимий перехід стану задачі. */
public class InvalidStateTransitionException extends CollabException {

  public InvalidStateTransitionException(TaskStatus from, TaskStatus to) {
    super("Недопустимий перехід стану задачі: " + from + " → " + to);
  }
}

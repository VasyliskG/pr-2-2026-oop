package ua.uzhnu.collab.enums;

/**
 * Статус задачі у канбан-дошці.
 *
 * <p>Допустимі переходи стану (state machine):
 * <pre>
 *   TODO ──→ IN_PROGRESS ──→ DONE
 *    ↑            ↕           ↕
 *    └────────────┘───────────┘
 * </pre>
 *
 * @see ua.uzhnu.collab.service.TaskService#changeStatus
 */
public enum TaskStatus {

  /** Задача очікує на початок виконання. */
  TODO,

  /** Задача виконується. */
  IN_PROGRESS,

  /** Задача виконана. */
  DONE
}

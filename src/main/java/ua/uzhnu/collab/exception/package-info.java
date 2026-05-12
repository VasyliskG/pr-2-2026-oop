/**
 * Ієрархія виключень платформи.
 *
 * <p>Усі виключення є {@code RuntimeException} і успадковують
 * {@link ua.uzhnu.collab.exception.CollabException}.
 *
 * <pre>
 * CollabException (базове)
 * ├── AccessDeniedException      — недостатня роль або не учасник команди
 * ├── DuplicateEntityException   — порушення UNIQUE (username, email, membership)
 * ├── EntityNotFoundException    — сутність не знайдена за ID або полем
 * └── InvalidStateTransitionException — недопустимий перехід статусу задачі
 * </pre>
 */
package ua.uzhnu.collab.exception;

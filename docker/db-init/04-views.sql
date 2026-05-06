-- =====================================================================
-- АНАЛІТИЧНІ ВІРТУАЛЬНІ ТАБЛИЦІ (VIEWS)
-- Використовуються у звітних модулях застосунку та UI-дашбордах
-- =====================================================================

-- =====================================================================
-- VIEW 1: Статистика команд (агрегована)
-- =====================================================================
-- Зведена інформація по кожній команді: учасники, задачі, файли,
-- активність чату. Використовується на сторінці «Огляд команди».

CREATE OR REPLACE VIEW v_team_statistics AS
SELECT
    t.id                                                            AS team_id,
    t.name                                                          AS team_name,
    t.created_at                                                    AS team_created_at,
    u.full_name                                                     AS owner_name,
    COALESCE(member_stats.member_count, 0)                          AS member_count,
    COALESCE(task_stats.total_tasks, 0)                             AS total_tasks,
    COALESCE(task_stats.todo_count, 0)                              AS todo_count,
    COALESCE(task_stats.in_progress_count, 0)                       AS in_progress_count,
    COALESCE(task_stats.done_count, 0)                              AS done_count,
    COALESCE(task_stats.overdue_count, 0)                           AS overdue_count,
    COALESCE(file_stats.file_count, 0)                              AS file_count,
    COALESCE(file_stats.total_size_bytes, 0)                        AS total_size_bytes,
    COALESCE(msg_stats.message_count, 0)                            AS message_count,
    msg_stats.last_message_at                                       AS last_message_at
FROM teams t
LEFT JOIN users u ON u.id = t.created_by
LEFT JOIN (
    SELECT team_id, COUNT(*) AS member_count
    FROM team_members
    GROUP BY team_id
) member_stats ON member_stats.team_id = t.id
LEFT JOIN (
    SELECT
        team_id,
        COUNT(*)                                              AS total_tasks,
        COUNT(*) FILTER (WHERE status = 'TODO')               AS todo_count,
        COUNT(*) FILTER (WHERE status = 'IN_PROGRESS')        AS in_progress_count,
        COUNT(*) FILTER (WHERE status = 'DONE')               AS done_count,
        COUNT(*) FILTER (WHERE status <> 'DONE'
                          AND due_date IS NOT NULL
                          AND due_date < CURRENT_TIMESTAMP)   AS overdue_count
    FROM tasks
    GROUP BY team_id
) task_stats ON task_stats.team_id = t.id
LEFT JOIN (
    SELECT team_id, COUNT(*) AS file_count, SUM(file_size) AS total_size_bytes
    FROM files
    GROUP BY team_id
) file_stats ON file_stats.team_id = t.id
LEFT JOIN (
    SELECT team_id, COUNT(*) AS message_count, MAX(created_at) AS last_message_at
    FROM chat_messages
    GROUP BY team_id
) msg_stats ON msg_stats.team_id = t.id;

COMMENT ON VIEW v_team_statistics
    IS 'Зведена статистика команди: учасники, задачі за статусами, файли, повідомлення';


-- =====================================================================
-- VIEW 2: Робоче навантаження користувачів
-- =====================================================================
-- Кількість призначених задач за статусами для кожного користувача

CREATE OR REPLACE VIEW v_user_workload AS
SELECT
    u.id                                              AS user_id,
    u.full_name                                       AS full_name,
    u.username                                        AS username,
    COALESCE(team_stats.team_count, 0)                AS team_count,
    COALESCE(task_stats.todo_count, 0)                AS todo_count,
    COALESCE(task_stats.in_progress_count, 0)         AS in_progress_count,
    COALESCE(task_stats.done_count, 0)                AS done_count,
    COALESCE(task_stats.overdue_count, 0)             AS overdue_count,
    COALESCE(task_stats.total_active, 0)              AS total_active_tasks
FROM users u
LEFT JOIN (
    SELECT user_id, COUNT(*) AS team_count
    FROM team_members
    GROUP BY user_id
) team_stats ON team_stats.user_id = u.id
LEFT JOIN (
    SELECT
        ta.user_id,
        COUNT(*) FILTER (WHERE t.status = 'TODO')                       AS todo_count,
        COUNT(*) FILTER (WHERE t.status = 'IN_PROGRESS')                AS in_progress_count,
        COUNT(*) FILTER (WHERE t.status = 'DONE')                       AS done_count,
        COUNT(*) FILTER (WHERE t.status <> 'DONE'
                          AND t.due_date IS NOT NULL
                          AND t.due_date < CURRENT_TIMESTAMP)           AS overdue_count,
        COUNT(*) FILTER (WHERE t.status IN ('TODO', 'IN_PROGRESS'))     AS total_active
    FROM task_assignees ta
    JOIN tasks t ON t.id = ta.task_id
    GROUP BY ta.user_id
) task_stats ON task_stats.user_id = u.id;

COMMENT ON VIEW v_user_workload
    IS 'Робоче навантаження кожного користувача: кількість команд та задач за статусами';


-- =====================================================================
-- VIEW 3: Прострочені задачі
-- =====================================================================
-- Задачі, що мають дедлайн і не виконані, з вказанням днів прострочення

CREATE OR REPLACE VIEW v_overdue_tasks AS
SELECT
    t.id                                                              AS task_id,
    t.title                                                           AS task_title,
    t.priority                                                        AS priority,
    t.due_date                                                        AS due_date,
    EXTRACT(DAY FROM (CURRENT_TIMESTAMP - t.due_date))::INTEGER       AS days_overdue,
    tm.id                                                             AS team_id,
    tm.name                                                           AS team_name,
    STRING_AGG(u.full_name, ', ' ORDER BY u.full_name)                AS assignees
FROM tasks t
JOIN teams tm           ON tm.id = t.team_id
LEFT JOIN task_assignees ta ON ta.task_id = t.id
LEFT JOIN users u           ON u.id = ta.user_id
WHERE t.status <> 'DONE'
  AND t.due_date IS NOT NULL
  AND t.due_date < CURRENT_TIMESTAMP
GROUP BY t.id, t.title, t.priority, t.due_date, tm.id, tm.name
ORDER BY days_overdue DESC, t.priority DESC;

COMMENT ON VIEW v_overdue_tasks
    IS 'Прострочені невиконані задачі з кількістю днів прострочення';


-- =====================================================================
-- VIEW 4: Активність команд (нещодавня)
-- =====================================================================
-- Останні події по кожній команді: задача, повідомлення, файл

CREATE OR REPLACE VIEW v_recent_team_activity AS
SELECT
    t.id                          AS team_id,
    t.name                        AS team_name,
    (SELECT MAX(created_at) FROM tasks         WHERE team_id = t.id)  AS last_task_at,
    (SELECT MAX(created_at) FROM chat_messages WHERE team_id = t.id)  AS last_message_at,
    (SELECT MAX(uploaded_at) FROM files        WHERE team_id = t.id)  AS last_file_at,
    GREATEST(
        COALESCE((SELECT MAX(created_at)  FROM tasks         WHERE team_id = t.id), '1970-01-01'),
        COALESCE((SELECT MAX(created_at)  FROM chat_messages WHERE team_id = t.id), '1970-01-01'),
        COALESCE((SELECT MAX(uploaded_at) FROM files         WHERE team_id = t.id), '1970-01-01')
    ) AS last_activity_at
FROM teams t
ORDER BY last_activity_at DESC;

COMMENT ON VIEW v_recent_team_activity
    IS 'Час останньої активності кожної команди (задачі, повідомлення, файли)';


-- =====================================================================
-- VIEW 5: Деревовидний чат (плоска проєкція з контекстом відповіді)
-- =====================================================================
-- Кожне повідомлення з контекстом батьківського повідомлення (якщо це відповідь)

CREATE OR REPLACE VIEW v_chat_with_replies AS
SELECT
    cm.id                       AS message_id,
    cm.team_id                  AS team_id,
    cm.user_id                  AS user_id,
    u.full_name                 AS author_name,
    cm.content                  AS content,
    cm.created_at               AS created_at,
    cm.parent_message_id        AS parent_message_id,
    parent_msg.content          AS parent_content,
    parent_user.full_name       AS parent_author_name
FROM chat_messages cm
JOIN users u                ON u.id = cm.user_id
LEFT JOIN chat_messages parent_msg ON parent_msg.id = cm.parent_message_id
LEFT JOIN users parent_user        ON parent_user.id = parent_msg.user_id;

COMMENT ON VIEW v_chat_with_replies
    IS 'Повідомлення чату з контекстом батьківського повідомлення (для відображення відповідей)';


-- =====================================================================
-- VIEW 6: Топ виконавців
-- =====================================================================
-- Користувачі, ранжовані за кількістю виконаних задач (DONE)

CREATE OR REPLACE VIEW v_top_performers AS
SELECT
    u.id                                          AS user_id,
    u.full_name                                   AS full_name,
    COUNT(*) FILTER (WHERE t.status = 'DONE')     AS completed_tasks,
    COUNT(*)                                      AS total_assigned_tasks,
    ROUND(
        COUNT(*) FILTER (WHERE t.status = 'DONE')::NUMERIC * 100.0 /
        NULLIF(COUNT(*), 0),
        1
    )                                             AS completion_rate_percent
FROM users u
JOIN task_assignees ta ON ta.user_id = u.id
JOIN tasks t           ON t.id = ta.task_id
GROUP BY u.id, u.full_name
HAVING COUNT(*) > 0
ORDER BY completed_tasks DESC, completion_rate_percent DESC;

COMMENT ON VIEW v_top_performers
    IS 'Рейтинг виконавців за кількістю завершених задач та відсотком виконання';


-- =====================================================================
-- VIEW 7: Розгорнутий перелік задач з усіма контекстними даними
-- =====================================================================
-- Готовий датасет для відображення задач у канбан-дошці UI

CREATE OR REPLACE VIEW v_tasks_full AS
SELECT
    t.id                                              AS task_id,
    t.title                                           AS task_title,
    t.description                                     AS task_description,
    t.status                                          AS status,
    t.priority                                        AS priority,
    t.due_date                                        AS due_date,
    t.created_at                                      AS created_at,
    t.updated_at                                      AS updated_at,
    tm.id                                             AS team_id,
    tm.name                                           AS team_name,
    creator.full_name                                 AS creator_name,
    STRING_AGG(DISTINCT assignee.full_name, ', ')     AS assignees,
    COUNT(DISTINCT tc.id)                             AS comment_count,
    COUNT(DISTINCT f.id)                              AS file_count,
    CASE
        WHEN t.status <> 'DONE' AND t.due_date IS NOT NULL AND t.due_date < CURRENT_TIMESTAMP
        THEN TRUE ELSE FALSE
    END                                               AS is_overdue
FROM tasks t
JOIN teams tm                ON tm.id = t.team_id
JOIN users creator           ON creator.id = t.created_by
LEFT JOIN task_assignees ta  ON ta.task_id = t.id
LEFT JOIN users assignee     ON assignee.id = ta.user_id
LEFT JOIN task_comments tc   ON tc.task_id = t.id
LEFT JOIN files f            ON f.task_id = t.id
GROUP BY t.id, t.title, t.description, t.status, t.priority, t.due_date,
         t.created_at, t.updated_at, tm.id, tm.name, creator.full_name;

COMMENT ON VIEW v_tasks_full
    IS 'Повний контекст задачі: команда, творець, виконавці, кількість коментарів та файлів';


-- =====================================================================
-- КІНЕЦЬ СКРИПТА ВІРТУАЛЬНИХ ТАБЛИЦЬ
-- =====================================================================

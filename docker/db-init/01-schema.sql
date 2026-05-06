-- =====================================================================
-- ПЛАТФОРМА КОМАНДНОЇ РОБОТИ ДЛЯ СТУДЕНТІВ
-- DDL-скрипт створення схеми бази даних (PostgreSQL 14+)
-- =====================================================================

-- Використовується snake_case для ідентифікаторів
-- Ключові слова SQL — UPPERCASE
-- Усі сутності — у 3НФ

-- =====================================================================
-- ОЧИЩЕННЯ (для повторного запуску в середовищі розробки)
-- =====================================================================

DROP TABLE IF EXISTS files            CASCADE;
DROP TABLE IF EXISTS chat_messages    CASCADE;
DROP TABLE IF EXISTS task_comments    CASCADE;
DROP TABLE IF EXISTS task_assignees   CASCADE;
DROP TABLE IF EXISTS tasks            CASCADE;
DROP TABLE IF EXISTS team_members     CASCADE;
DROP TABLE IF EXISTS teams            CASCADE;
DROP TABLE IF EXISTS users            CASCADE;

DROP TYPE IF EXISTS task_priority CASCADE;
DROP TYPE IF EXISTS task_status   CASCADE;
DROP TYPE IF EXISTS team_role     CASCADE;

-- =====================================================================
-- КОРИСТУВАЦЬКІ ТИПИ ДАНИХ (ENUMs)
-- =====================================================================

CREATE TYPE team_role AS ENUM ('OWNER', 'ADMIN', 'MEMBER');

CREATE TYPE task_status AS ENUM ('TODO', 'IN_PROGRESS', 'DONE');

CREATE TYPE task_priority AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');


-- =====================================================================
-- ТАБЛИЦЯ 1: USERS — користувачі системи
-- =====================================================================

CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL UNIQUE,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_users_username_length
        CHECK (LENGTH(username) >= 3),

    CONSTRAINT chk_users_email_format
        CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),

    CONSTRAINT chk_users_full_name_not_empty
        CHECK (LENGTH(TRIM(full_name)) > 0)
);

COMMENT ON TABLE users IS 'Зареєстровані користувачі платформи (студенти)';


-- =====================================================================
-- ТАБЛИЦЯ 2: TEAMS — команди (студентські колективи)
-- =====================================================================

CREATE TABLE teams (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    description     TEXT,
    created_by      BIGINT          NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_teams_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,

    CONSTRAINT chk_teams_name_not_empty
        CHECK (LENGTH(TRIM(name)) > 0)
);

COMMENT ON TABLE teams IS 'Команди студентів для виконання спільних проєктів';


-- =====================================================================
-- ТАБЛИЦЯ 3: TEAM_MEMBERS — учасники команд (зв'язок N:M User <-> Team)
-- =====================================================================

CREATE TABLE team_members (
    team_id     BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    role        team_role   NOT NULL DEFAULT 'MEMBER',
    joined_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_team_members
        PRIMARY KEY (team_id, user_id),

    CONSTRAINT fk_tm_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,

    CONSTRAINT fk_tm_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

COMMENT ON TABLE team_members IS 'Учасники команд із вказаною роллю (Власник, Адмін, Учасник)';


-- =====================================================================
-- ТАБЛИЦЯ 4: TASKS — задачі команди
-- =====================================================================

CREATE TABLE tasks (
    id              BIGSERIAL       PRIMARY KEY,
    team_id         BIGINT          NOT NULL,
    title           VARCHAR(200)    NOT NULL,
    description     TEXT,
    status          task_status     NOT NULL DEFAULT 'TODO',
    priority        task_priority   NOT NULL DEFAULT 'MEDIUM',
    due_date        TIMESTAMP,
    created_by      BIGINT          NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tasks_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,

    CONSTRAINT fk_tasks_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,

    CONSTRAINT chk_tasks_title_not_empty
        CHECK (LENGTH(TRIM(title)) > 0)
);

COMMENT ON TABLE tasks IS 'Задачі (таски) у межах команди';


-- =====================================================================
-- ТАБЛИЦЯ 5: TASK_ASSIGNEES — виконавці задач (зв'язок N:M User <-> Task)
-- =====================================================================

CREATE TABLE task_assignees (
    task_id         BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    assigned_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_task_assignees
        PRIMARY KEY (task_id, user_id),

    CONSTRAINT fk_ta_task
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,

    CONSTRAINT fk_ta_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

COMMENT ON TABLE task_assignees IS 'Призначення виконавців на задачі (одна задача може мати декількох виконавців)';


-- =====================================================================
-- ТАБЛИЦЯ 6: TASK_COMMENTS — коментарі до задач
-- =====================================================================

CREATE TABLE task_comments (
    id          BIGSERIAL   PRIMARY KEY,
    task_id     BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    content     TEXT        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tc_task
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,

    CONSTRAINT fk_tc_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,

    CONSTRAINT chk_tc_content_not_empty
        CHECK (LENGTH(TRIM(content)) > 0)
);

COMMENT ON TABLE task_comments IS 'Коментарі учасників команди до задач';


-- =====================================================================
-- ТАБЛИЦЯ 7: CHAT_MESSAGES — повідомлення командного чату
-- =====================================================================

CREATE TABLE chat_messages (
    id                  BIGSERIAL   PRIMARY KEY,
    team_id             BIGINT      NOT NULL,
    user_id             BIGINT      NOT NULL,
    content             TEXT        NOT NULL,
    parent_message_id   BIGINT,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cm_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,

    CONSTRAINT fk_cm_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,

    CONSTRAINT fk_cm_parent
        FOREIGN KEY (parent_message_id) REFERENCES chat_messages(id) ON DELETE SET NULL,

    CONSTRAINT chk_cm_content_not_empty
        CHECK (LENGTH(TRIM(content)) > 0),

    CONSTRAINT chk_cm_no_self_reference
        CHECK (parent_message_id IS NULL OR parent_message_id <> id)
);

COMMENT ON TABLE chat_messages IS 'Повідомлення у командному чаті з підтримкою відповідей (parent_message_id)';


-- =====================================================================
-- ТАБЛИЦЯ 8: FILES — файли команди / задачі
-- =====================================================================

CREATE TABLE files (
    id              BIGSERIAL       PRIMARY KEY,
    team_id         BIGINT          NOT NULL,
    task_id         BIGINT,
    uploaded_by     BIGINT          NOT NULL,
    file_name       VARCHAR(255)    NOT NULL,
    storage_path    VARCHAR(500)    NOT NULL,
    file_size       BIGINT          NOT NULL,
    mime_type       VARCHAR(100)    NOT NULL,
    uploaded_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_files_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,

    CONSTRAINT fk_files_task
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL,

    CONSTRAINT fk_files_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE RESTRICT,

    CONSTRAINT chk_files_size_positive
        CHECK (file_size > 0),

    CONSTRAINT chk_files_name_not_empty
        CHECK (LENGTH(TRIM(file_name)) > 0)
);

COMMENT ON TABLE files IS 'Файли, завантажені учасниками команди (можуть бути прив''язані до задач)';


-- =====================================================================
-- ІНДЕКСИ ДЛЯ ОПТИМІЗАЦІЇ ЗАПИТІВ
-- =====================================================================

-- Пошук учасників за користувачем (зворотний бік N:M)
CREATE INDEX idx_team_members_user
    ON team_members(user_id);

-- Усі задачі команди (часта операція)
CREATE INDEX idx_tasks_team
    ON tasks(team_id);

-- Фільтрація задач за статусом
CREATE INDEX idx_tasks_status
    ON tasks(status);

-- Сортування за дедлайном (тільки для задач, де він заданий)
CREATE INDEX idx_tasks_due_date
    ON tasks(due_date)
    WHERE due_date IS NOT NULL;

-- Усі задачі, призначені користувачу (зворотний бік N:M)
CREATE INDEX idx_task_assignees_user
    ON task_assignees(user_id);

-- Коментарі задачі в хронологічному порядку
CREATE INDEX idx_task_comments_task
    ON task_comments(task_id, created_at);

-- Чат: останні повідомлення команди (зворотний порядок)
CREATE INDEX idx_chat_messages_team_time
    ON chat_messages(team_id, created_at DESC);

-- Дочірні повідомлення (відповіді) — ефективний JOIN
CREATE INDEX idx_chat_messages_parent
    ON chat_messages(parent_message_id)
    WHERE parent_message_id IS NOT NULL;

-- Файли команди
CREATE INDEX idx_files_team
    ON files(team_id);

-- Файли, прив'язані до задачі
CREATE INDEX idx_files_task
    ON files(task_id)
    WHERE task_id IS NOT NULL;


-- =====================================================================
-- ТРИГЕРИ: автоматичне оновлення поля updated_at
-- =====================================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_teams_updated_at
    BEFORE UPDATE ON teams
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();


-- =====================================================================
-- ВІРТУАЛЬНА ТАБЛИЦЯ: задачі з виконавцями (для UI)
-- =====================================================================

CREATE OR REPLACE VIEW v_tasks_with_assignees AS
SELECT
    t.id              AS task_id,
    t.team_id,
    t.title,
    t.status,
    t.priority,
    t.due_date,
    t.created_at,
    STRING_AGG(u.full_name, ', ' ORDER BY u.full_name) AS assignees
FROM tasks t
LEFT JOIN task_assignees ta ON ta.task_id = t.id
LEFT JOIN users u           ON u.id = ta.user_id
GROUP BY t.id, t.team_id, t.title, t.status, t.priority, t.due_date, t.created_at;

COMMENT ON VIEW v_tasks_with_assignees IS 'Задачі з конкатенованим списком імен виконавців — для відображення у канбан-дошці';


-- =====================================================================
-- КІНЕЦЬ DDL-СКРИПТА
-- =====================================================================

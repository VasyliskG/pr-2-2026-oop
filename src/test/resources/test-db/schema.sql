-- =====================================================================
-- ТЕСТОВА СХЕМА — спрощена версія для інтеграційних тестів.
-- Без тригерів і збережених процедур (timestamps керуються JDBC-репозиторіями).
-- Без CREATE INDEX (не впливає на коректність, прискорює setup).
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

-- ENUMs (відповідають Java-enum-ам у пакеті ua.uzhnu.collab.enums)
CREATE TYPE team_role     AS ENUM ('OWNER', 'ADMIN', 'MEMBER');
CREATE TYPE task_status   AS ENUM ('TODO', 'IN_PROGRESS', 'DONE');
CREATE TYPE task_priority AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');

-- USERS
CREATE TABLE users (
    id              BIGSERIAL     PRIMARY KEY,
    username        VARCHAR(50)   NOT NULL UNIQUE,
    email           VARCHAR(255)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255)  NOT NULL,
    full_name       VARCHAR(255)  NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- TEAMS
CREATE TABLE teams (
    id              BIGSERIAL     PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    description     TEXT,
    created_by      BIGINT        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- TEAM_MEMBERS (N:M users <-> teams з атрибутом role)
CREATE TABLE team_members (
    team_id         BIGINT        NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id         BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            team_role     NOT NULL DEFAULT 'MEMBER',
    joined_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (team_id, user_id)
);

-- TASKS
CREATE TABLE tasks (
    id              BIGSERIAL       PRIMARY KEY,
    team_id         BIGINT          NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    title           VARCHAR(200)    NOT NULL,
    description     TEXT,
    status          task_status     NOT NULL DEFAULT 'TODO',
    priority        task_priority   NOT NULL DEFAULT 'MEDIUM',
    due_date        TIMESTAMP,
    created_by      BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- TASK_ASSIGNEES (N:M tasks <-> users)
CREATE TABLE task_assignees (
    task_id         BIGINT          NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id, user_id)
);

-- TASK_COMMENTS
CREATE TABLE task_comments (
    id              BIGSERIAL       PRIMARY KEY,
    task_id         BIGINT          NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         TEXT            NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CHAT_MESSAGES (з рекурсивним зв'язком parent_message_id)
CREATE TABLE chat_messages (
    id                  BIGSERIAL   PRIMARY KEY,
    team_id             BIGINT      NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id             BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content             TEXT        NOT NULL,
    parent_message_id   BIGINT      REFERENCES chat_messages(id) ON DELETE SET NULL,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- FILES
CREATE TABLE files (
    id              BIGSERIAL       PRIMARY KEY,
    team_id         BIGINT          NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    task_id         BIGINT          REFERENCES tasks(id) ON DELETE SET NULL,
    uploaded_by     BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    file_name       VARCHAR(255)    NOT NULL,
    storage_path    VARCHAR(500)    NOT NULL,
    file_size       BIGINT          NOT NULL CHECK (file_size > 0),
    mime_type       VARCHAR(100)    NOT NULL,
    uploaded_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- ЗБЕРЕЖЕНІ ПРОЦЕДУРИ ТА ФУНКЦІЇ
-- Автоматизація типових операцій з контролем цілісності даних
-- =====================================================================

-- =====================================================================
-- ФУНКЦІЯ 1: Додавання учасника до команди з валідацією
-- =====================================================================
-- Перевіряє наявність команди, користувача та дублікат участі.
-- Повертає TRUE при успіху, FALSE — якщо учасник уже у команді.
-- Викидає виняток у разі відсутності команди або користувача.

CREATE OR REPLACE FUNCTION fn_add_team_member(
    p_team_id BIGINT,
    p_user_id BIGINT,
    p_role    team_role DEFAULT 'MEMBER'
) RETURNS BOOLEAN AS $$
BEGIN
    -- Перевірка існування команди
    IF NOT EXISTS (SELECT 1 FROM teams WHERE id = p_team_id) THEN
        RAISE EXCEPTION 'Команда з ідентифікатором % не існує', p_team_id;
    END IF;

    -- Перевірка існування користувача
    IF NOT EXISTS (SELECT 1 FROM users WHERE id = p_user_id) THEN
        RAISE EXCEPTION 'Користувач з ідентифікатором % не існує', p_user_id;
    END IF;

    -- Перевірка дубліката
    IF EXISTS (
        SELECT 1 FROM team_members
        WHERE team_id = p_team_id AND user_id = p_user_id
    ) THEN
        RETURN FALSE;
    END IF;

    -- Додавання учасника
    INSERT INTO team_members (team_id, user_id, role)
    VALUES (p_team_id, p_user_id, p_role);

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_add_team_member(BIGINT, BIGINT, team_role)
    IS 'Додає користувача до команди з вказаною роллю; повертає FALSE при дублікаті';


-- =====================================================================
-- ФУНКЦІЯ 2: Видалення учасника з команди
-- =====================================================================
-- Видаляє учасника, але блокує видалення останнього власника команди

CREATE OR REPLACE FUNCTION fn_remove_team_member(
    p_team_id BIGINT,
    p_user_id BIGINT
) RETURNS BOOLEAN AS $$
DECLARE
    v_user_role     team_role;
    v_owner_count   INTEGER;
BEGIN
    -- Отримання поточної ролі учасника
    SELECT role INTO v_user_role
    FROM team_members
    WHERE team_id = p_team_id AND user_id = p_user_id;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    -- Захист останнього власника
    IF v_user_role = 'OWNER' THEN
        SELECT COUNT(*) INTO v_owner_count
        FROM team_members
        WHERE team_id = p_team_id AND role = 'OWNER';

        IF v_owner_count <= 1 THEN
            RAISE EXCEPTION 'Неможливо видалити останнього власника команди %', p_team_id;
        END IF;
    END IF;

    DELETE FROM team_members
    WHERE team_id = p_team_id AND user_id = p_user_id;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_remove_team_member(BIGINT, BIGINT)
    IS 'Видаляє учасника з команди з захистом останнього власника';


-- =====================================================================
-- ФУНКЦІЯ 3: Призначення задачі виконавцю
-- =====================================================================
-- Перевіряє, що користувач є учасником команди задачі

CREATE OR REPLACE FUNCTION fn_assign_task(
    p_task_id BIGINT,
    p_user_id BIGINT
) RETURNS BOOLEAN AS $$
DECLARE
    v_team_id BIGINT;
BEGIN
    -- Отримання команди задачі
    SELECT team_id INTO v_team_id
    FROM tasks
    WHERE id = p_task_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Задача з ідентифікатором % не існує', p_task_id;
    END IF;

    -- Перевірка членства у команді
    IF NOT EXISTS (
        SELECT 1 FROM team_members
        WHERE team_id = v_team_id AND user_id = p_user_id
    ) THEN
        RAISE EXCEPTION 'Користувач % не є учасником команди задачі', p_user_id;
    END IF;

    -- Перевірка дубліката призначення
    IF EXISTS (
        SELECT 1 FROM task_assignees
        WHERE task_id = p_task_id AND user_id = p_user_id
    ) THEN
        RETURN FALSE;
    END IF;

    INSERT INTO task_assignees (task_id, user_id)
    VALUES (p_task_id, p_user_id);

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_assign_task(BIGINT, BIGINT)
    IS 'Призначає виконавця задачі з перевіркою членства у команді';


-- =====================================================================
-- ФУНКЦІЯ 4: Зміна статусу задачі з валідацією переходу
-- =====================================================================
-- Дозволені переходи:
--   TODO → IN_PROGRESS, TODO → DONE
--   IN_PROGRESS → DONE, IN_PROGRESS → TODO
--   DONE → IN_PROGRESS (повторне відкриття)

CREATE OR REPLACE FUNCTION fn_change_task_status(
    p_task_id    BIGINT,
    p_new_status task_status
) RETURNS BOOLEAN AS $$
DECLARE
    v_current_status task_status;
    v_valid          BOOLEAN := FALSE;
BEGIN
    SELECT status INTO v_current_status FROM tasks WHERE id = p_task_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Задача з ідентифікатором % не існує', p_task_id;
    END IF;

    IF v_current_status = p_new_status THEN
        RETURN FALSE;  -- немає змін
    END IF;

    -- Валідація переходу
    v_valid := (
        (v_current_status = 'TODO'        AND p_new_status IN ('IN_PROGRESS', 'DONE')) OR
        (v_current_status = 'IN_PROGRESS' AND p_new_status IN ('TODO', 'DONE'))        OR
        (v_current_status = 'DONE'        AND p_new_status = 'IN_PROGRESS')
    );

    IF NOT v_valid THEN
        RAISE EXCEPTION 'Недопустимий перехід стану: % → %', v_current_status, p_new_status;
    END IF;

    UPDATE tasks SET status = p_new_status WHERE id = p_task_id;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_change_task_status(BIGINT, task_status)
    IS 'Змінює статус задачі з валідацією допустимості переходу';


-- =====================================================================
-- ФУНКЦІЯ 5: Статистика команди
-- =====================================================================
-- Повертає composite-рядок зі зведеною статистикою команди

CREATE OR REPLACE FUNCTION fn_get_team_statistics(p_team_id BIGINT)
RETURNS TABLE (
    team_id           BIGINT,
    team_name         VARCHAR,
    member_count      INTEGER,
    total_tasks       INTEGER,
    todo_tasks        INTEGER,
    in_progress_tasks INTEGER,
    done_tasks        INTEGER,
    overdue_tasks     INTEGER,
    total_files       INTEGER,
    total_messages    INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        t.id,
        t.name,
        (SELECT COUNT(*)::INTEGER FROM team_members WHERE team_members.team_id = t.id),
        (SELECT COUNT(*)::INTEGER FROM tasks WHERE tasks.team_id = t.id),
        (SELECT COUNT(*)::INTEGER FROM tasks WHERE tasks.team_id = t.id AND tasks.status = 'TODO'),
        (SELECT COUNT(*)::INTEGER FROM tasks WHERE tasks.team_id = t.id AND tasks.status = 'IN_PROGRESS'),
        (SELECT COUNT(*)::INTEGER FROM tasks WHERE tasks.team_id = t.id AND tasks.status = 'DONE'),
        (SELECT COUNT(*)::INTEGER FROM tasks
            WHERE tasks.team_id = t.id
              AND tasks.status <> 'DONE'
              AND tasks.due_date IS NOT NULL
              AND tasks.due_date < CURRENT_TIMESTAMP),
        (SELECT COUNT(*)::INTEGER FROM files WHERE files.team_id = t.id),
        (SELECT COUNT(*)::INTEGER FROM chat_messages WHERE chat_messages.team_id = t.id)
    FROM teams t
    WHERE t.id = p_team_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_get_team_statistics(BIGINT)
    IS 'Повертає зведену статистику команди: учасники, задачі за статусами, файли, повідомлення';


-- =====================================================================
-- ФУНКЦІЯ 6: Завантаженість користувача
-- =====================================================================
-- Повертає кількість активних задач користувача за статусами та командами

CREATE OR REPLACE FUNCTION fn_get_user_workload(p_user_id BIGINT)
RETURNS TABLE (
    user_id            BIGINT,
    full_name          VARCHAR,
    teams_count        INTEGER,
    todo_tasks         INTEGER,
    in_progress_tasks  INTEGER,
    done_tasks         INTEGER,
    overdue_tasks      INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        u.id,
        u.full_name,
        (SELECT COUNT(*)::INTEGER FROM team_members WHERE team_members.user_id = u.id),
        (SELECT COUNT(*)::INTEGER FROM task_assignees ta
            JOIN tasks t ON t.id = ta.task_id
            WHERE ta.user_id = u.id AND t.status = 'TODO'),
        (SELECT COUNT(*)::INTEGER FROM task_assignees ta
            JOIN tasks t ON t.id = ta.task_id
            WHERE ta.user_id = u.id AND t.status = 'IN_PROGRESS'),
        (SELECT COUNT(*)::INTEGER FROM task_assignees ta
            JOIN tasks t ON t.id = ta.task_id
            WHERE ta.user_id = u.id AND t.status = 'DONE'),
        (SELECT COUNT(*)::INTEGER FROM task_assignees ta
            JOIN tasks t ON t.id = ta.task_id
            WHERE ta.user_id = u.id
              AND t.status <> 'DONE'
              AND t.due_date IS NOT NULL
              AND t.due_date < CURRENT_TIMESTAMP)
    FROM users u
    WHERE u.id = p_user_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_get_user_workload(BIGINT)
    IS 'Повертає робоче навантаження користувача: кількість команд та задач за статусами';


-- =====================================================================
-- ФУНКЦІЯ 7: Пошук задач за текстом
-- =====================================================================
-- Регістронезалежний пошук у назві та описі задач команди

CREATE OR REPLACE FUNCTION fn_search_tasks(
    p_team_id BIGINT,
    p_query   VARCHAR
) RETURNS TABLE (
    task_id     BIGINT,
    title       VARCHAR,
    status      task_status,
    priority    task_priority,
    due_date    TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT t.id, t.title, t.status, t.priority, t.due_date
    FROM tasks t
    WHERE t.team_id = p_team_id
      AND (
            t.title       ILIKE '%' || p_query || '%'
         OR t.description ILIKE '%' || p_query || '%'
      )
    ORDER BY
        CASE t.status
            WHEN 'IN_PROGRESS' THEN 1
            WHEN 'TODO'        THEN 2
            WHEN 'DONE'        THEN 3
        END,
        t.due_date NULLS LAST;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_search_tasks(BIGINT, VARCHAR)
    IS 'Пошук задач команди за фрагментом тексту в назві або описі (ILIKE)';


-- =====================================================================
-- ПРОЦЕДУРА: Видалення задачі з усіма пов'язаними даними
-- =====================================================================
-- На відміну від простого DELETE використовує транзакцію та
-- повертає кількість видалених пов'язаних об'єктів

CREATE OR REPLACE PROCEDURE proc_delete_task_with_related(
    IN  p_task_id            BIGINT,
    OUT p_deleted_assignees  INTEGER,
    OUT p_deleted_comments   INTEGER,
    OUT p_unlinked_files     INTEGER
) LANGUAGE plpgsql AS $$
BEGIN
    -- Збираємо лічильники
    SELECT COUNT(*)::INTEGER INTO p_deleted_assignees
    FROM task_assignees WHERE task_id = p_task_id;

    SELECT COUNT(*)::INTEGER INTO p_deleted_comments
    FROM task_comments WHERE task_id = p_task_id;

    SELECT COUNT(*)::INTEGER INTO p_unlinked_files
    FROM files WHERE task_id = p_task_id;

    -- Видалення (CASCADE та SET NULL спрацьовують автоматично через FK)
    DELETE FROM tasks WHERE id = p_task_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Задача з ідентифікатором % не існує', p_task_id;
    END IF;
END;
$$;

COMMENT ON PROCEDURE proc_delete_task_with_related(BIGINT, INTEGER, INTEGER, INTEGER)
    IS 'Видаляє задачу та повертає кількість каскадно видалених/відв''язаних об''єктів';


-- =====================================================================
-- КІНЕЦЬ СКРИПТА ЗБЕРЕЖЕНИХ ПРОЦЕДУР
-- =====================================================================

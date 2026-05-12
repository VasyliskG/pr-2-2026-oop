# Student Collab Platform

Десктопна платформа командної роботи для студентів: канбан-дошка задач, командний чат та спільне сховище файлів.

---

## Зміст

- [Опис проєкту](#опис-проєкту)
- [Технології](#технології)
- [Архітектура](#архітектура)
- [Збірка та запуск](#збірка-та-запуск)
- [Структура проєкту](#структура-проєкту)
- [Документація API](#документація-api)

---

## Опис проєкту

Student Collab Platform — JavaFX-застосунок із Spring Boot бекендом для організації спільної роботи в студентських командах.

**Основні можливості:**

| Модуль | Функціональність |
|--------|-----------------|
| Команди | Створення, запрошення учасників, ролі (OWNER / ADMIN / MEMBER) |
| Задачі | Канбан-дошка (TODO / IN\_PROGRESS / DONE), пріоритети, дедлайни, виконавці |
| Чат | Командний чат з підтримкою відповідей на повідомлення |
| Файли | Завантаження файлів до задач та спільного простору команди |
| Аналітика | Статистика команди, прострочені задачі, навантаження учасників |

---

## Технології

| Шар | Технологія | Версія |
|-----|-----------|--------|
| Мова | Java | 21 |
| UI | JavaFX + FXML | 21.0.5 |
| IoC / DI | Spring Boot | 3.3.5 |
| БД | PostgreSQL | 15+ |
| JDBC | Spring JDBC (`NamedParameterJdbcTemplate`) | 3.3.5 |
| Безпека | Spring Security Crypto (BCrypt) | — |
| AOP | Spring AOP | — |
| Шаблони коду | Lombok | 1.18.34 |
| Тести | JUnit 5, Mockito, TestFX | — |
| Білд | Maven | 3.9+ |

---

## Архітектура

```
┌─────────────────────────────────────────────────────────┐
│                     JavaFX UI Layer                      │
│  Controllers (FXML)  ←→  ViewModels (JavaFX Properties) │
└──────────────────────────┬──────────────────────────────┘
                           │ Spring DI
┌──────────────────────────▼──────────────────────────────┐
│                    Service Layer                          │
│  UserService · TeamService · TaskService                 │
│  FileService · ChatService                               │
│  + LoggingAspect (AOP, вимірювання часу виконання)       │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│                  Repository Layer                         │
│  NamedParameterJdbcTemplate → PostgreSQL                 │
│  UserRepo · TeamRepo · TaskRepo · AppFileRepo · ...      │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│                    PostgreSQL 15+                         │
│  users · teams · team_members · tasks · task_assignees   │
│  task_comments · chat_messages · files                   │
└─────────────────────────────────────────────────────────┘
```

**Патерни проєктування:**

- **MVVM** — ViewModels з JavaFX Properties для реактивного зв'язування
- **Repository** — ізоляція SQL-запитів від бізнес-логіки
- **DTO** — Java Records для передачі даних між шарами
- **Factory Method** — `DtoMapper`, `DialogHelper`
- **State Machine** — переходи статусів задачі (`TODO → IN_PROGRESS ↔ DONE`)
- **AOP** — `LoggingAspect` для наскрізного логування сервісів

---

## Збірка та запуск

### Вимоги

- Java 21+
- Maven 3.9+
- PostgreSQL 15+

### 1. Налаштування бази даних

```sql
CREATE DATABASE student_collab;
CREATE USER collab_user WITH PASSWORD 'collab_secret';
GRANT ALL PRIVILEGES ON DATABASE student_collab TO collab_user;
```

### 2. Конфігурація

`src/main/resources/application.yml` (або змінна `DB_PASSWORD`):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/student_collab
    username: collab_user
    password: collab_secret
```

### 3. Збірка

```bash
mvn clean package -DskipTests
```

### 4. Запуск

```bash
mvn javafx:run
```

### 5. Тести

```bash
mvn test
```

### 6. HTML-документація Javadoc

```bash
mvn javadoc:javadoc
# Результат: target/reports/apidocs/index.html
```

---

## Структура проєкту

```
src/
├── main/
│   ├── java/ua/uzhnu/collab/
│   │   ├── aspect/          # LoggingAspect (AOP)
│   │   ├── config/          # NavigationService, SessionContext
│   │   ├── controller/      # JavaFX FXML-контролери
│   │   ├── dto/             # Dtos.java (Java Records)
│   │   ├── entity/          # POJO-сутності
│   │   ├── enums/           # TaskStatus, TaskPriority, TeamRole
│   │   ├── exception/       # Ієрархія CollabException
│   │   ├── repository/      # JDBC-репозиторії
│   │   ├── service/         # Бізнес-логіка
│   │   └── viewmodel/       # JavaFX ViewModels
│   └── resources/
│       ├── css/             # styles.css
│       └── fxml/            # FXML-розмітка екранів
└── test/
    └── java/ua/uzhnu/collab/
        ├── repository/      # Інтеграційні тести репозиторіїв
        └── service/         # Юніт-тести сервісів
```

---

## Документація API

```bash
mvn javadoc:javadoc
# Відкрити: target/reports/apidocs/index.html
```

Документація для користувачів — у директорії [`docs/`](docs/):

- [Встановлення](docs/installation.md)
- [Використання](docs/usage.md)
- [FAQ](docs/faq.md)

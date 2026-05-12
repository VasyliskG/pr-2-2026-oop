# Інструкція з встановлення

## Системні вимоги

| Компонент | Мінімальна версія |
|-----------|------------------|
| Java (JDK) | 21 |
| Maven | 3.9 |
| PostgreSQL | 15 |
| ОС | Windows 10 / macOS 12 / Ubuntu 22.04 |
| RAM | 512 МБ |

---

## Крок 1 — Встановлення Java 21

### Windows / macOS
Завантажити з [Adoptium](https://adoptium.net/) або [Oracle](https://www.oracle.com/java/technologies/downloads/).

Перевірити:
```bash
java -version
# java version "21.x.x"
```

### Ubuntu / Debian
```bash
sudo apt update && sudo apt install openjdk-21-jdk
```

---

## Крок 2 — Встановлення Maven

### Windows
Завантажити з [maven.apache.org](https://maven.apache.org/download.cgi), розпакувати, додати `bin/` до `PATH`.

### macOS
```bash
brew install maven
```

### Ubuntu
```bash
sudo apt install maven
```

Перевірити:
```bash
mvn -version
# Apache Maven 3.9.x
```

---

## Крок 3 — Встановлення PostgreSQL

### Windows / macOS
Завантажити з [postgresql.org](https://www.postgresql.org/download/).

### Ubuntu
```bash
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
```

---

## Крок 4 — Налаштування бази даних

Підключитися до PostgreSQL:
```bash
psql -U postgres
```

Виконати:
```sql
CREATE DATABASE student_collab;
CREATE USER collab_user WITH PASSWORD 'collab_secret';
GRANT ALL PRIVILEGES ON DATABASE student_collab TO collab_user;
\q
```

Ініціалізувати схему (якщо є DDL-файл):
```bash
psql -U collab_user -d student_collab -f schema.sql
```

---

## Крок 5 — Отримання вихідного коду

```bash
git clone <url-репозиторію>
cd pr-2-2026-oop
```

---

## Крок 6 — Конфігурація застосунку

Відредагувати `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/student_collab
    username: collab_user
    password: collab_secret   # або змінна середовища DB_PASSWORD
```

Альтернативно — через змінну середовища:
```bash
export DB_PASSWORD=collab_secret
```

---

## Крок 7 — Збірка та запуск

```bash
# Збірка (пропустити тести)
mvn clean package -DskipTests

# Запуск
mvn javafx:run
```

При першому запуску Maven завантажить залежності (~200 МБ).

---

## Перевірка встановлення

Після запуску має з'явитися вікно входу. Зареєструйте нового користувача та створіть команду.

---

## Вирішення проблем

**`Cannot connect to database`**
- Перевірте, що PostgreSQL запущено: `pg_isready`
- Перевірте логін/пароль у `application.yml`

**`JavaFX runtime components are missing`**
- Використовуйте `mvn javafx:run`, а не `java -jar`

**`Unsupported class file major version`**
- Перевірте версію Java: потрібна 21+

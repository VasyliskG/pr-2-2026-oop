# Reporting & Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add PDF and Excel export for three team reports (task list, statistics, member workload) accessible from a MenuButton in `TeamViewControllerV2`.

**Architecture:** A new `ReportService` Spring `@Service` generates `byte[]` output for all six report/format combinations by calling existing `TaskService` and `TeamService` methods — no new SQL. A `javafx.concurrent.Task` wraps each export call to keep the UI responsive; on success, `DialogHelper.showSaveFileDialog` prompts for save location.

**Tech Stack:** Apache PDFBox 3.0.3 (PDF generation), Apache POI 5.3.0 (Excel generation), DejaVuSans TTF (Cyrillic PDF text), Spring Boot 3.3.5, JavaFX 21, Mockito (unit tests).

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/ua/uzhnu/collab/service/ReportService.java` | All 6 export methods (3 reports × 2 formats) |
| Create | `src/test/java/ua/uzhnu/collab/service/ReportServiceTest.java` | Mockito unit tests — validates byte[] output loads as valid PDF/Excel |
| Create | `src/main/resources/fonts/DejaVuSans.ttf` | Cyrillic-capable font for PDFBox |
| Create | `src/main/resources/fonts/DejaVuSans-Bold.ttf` | Bold variant for table headers |
| Modify | `pom.xml` | Add PDFBox + POI dependencies |
| Modify | `src/main/java/ua/uzhnu/collab/repository/TaskRepository.java` | Add `findByAssigneeUserIdAndTeamId` |
| Modify | `src/test/java/ua/uzhnu/collab/repository/TaskRepositoryTest.java` | Test new query |
| Modify | `src/main/java/ua/uzhnu/collab/service/TeamService.java` | Add `getMemberWorkloads(Long teamId)` |
| Modify | `src/test/java/ua/uzhnu/collab/service/TeamServiceTest.java` | Test new method |
| Modify | `src/main/java/ua/uzhnu/collab/controller/DialogHelper.java` | Add `showSaveFileDialog` |
| Modify | `src/main/resources/fxml/team_view_v2.fxml` | Add `MenuButton` with 6 items |
| Modify | `src/main/java/ua/uzhnu/collab/controller/TeamViewControllerV2.java` | Inject `ReportService`, add handlers |

---

## Task 1: Add Maven Dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add PDFBox and POI to `pom.xml`**

In `pom.xml`, inside `<dependencies>`, add after the existing Lombok dependency:

```xml
        <!-- ==================== Звітність ==================== -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.3</version>
        </dependency>
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.3.0</version>
        </dependency>
```

- [ ] **Step 2: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS` with no errors. If you see `NoClassDefFoundError` or dependency conflicts, run `./mvnw dependency:tree | grep -E 'pdfbox|poi'` to diagnose.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add PDFBox 3.0.3 and POI 5.3.0 for reporting"
```

---

## Task 2: Bundle DejaVu Font for Cyrillic PDF Support

PDFBox Type1 fonts (Helvetica etc.) do not support Cyrillic. `PDType0Font` with a TTF file does.

**Files:**
- Create: `src/main/resources/fonts/DejaVuSans.ttf`
- Create: `src/main/resources/fonts/DejaVuSans-Bold.ttf`

- [ ] **Step 1: Copy DejaVu fonts from system**

```bash
mkdir -p src/main/resources/fonts
cp /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf src/main/resources/fonts/
cp /usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf src/main/resources/fonts/
```

If those paths don't exist, find them:
```bash
find /usr/share/fonts -name "DejaVuSans.ttf" 2>/dev/null | head -3
```

- [ ] **Step 2: Verify**

```bash
ls -lh src/main/resources/fonts/
```

Expected: two files, each ~700KB–1MB.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/fonts/
git commit -m "assets: bundle DejaVuSans TTF for Cyrillic PDF support"
```

---

## Task 3: Add `TaskRepository.findByAssigneeUserIdAndTeamId`

`TeamService.getMemberWorkloads` needs tasks filtered by both assignee AND team. The existing `findByAssigneeUserId` returns tasks across all teams — too broad.

**Files:**
- Modify: `src/test/java/ua/uzhnu/collab/repository/TaskRepositoryTest.java`
- Modify: `src/main/java/ua/uzhnu/collab/repository/TaskRepository.java`

- [ ] **Step 1: Write failing test**

In `TaskRepositoryTest`, add after the existing `findByAssigneeUserId` tests:

```java
  // =====================================================================
  // findByAssigneeUserIdAndTeamId
  // =====================================================================

  @Test
  @DisplayName("findByAssigneeUserIdAndTeamId: повертає лише задачі призначеного користувача в конкретній команді")
  void findByAssigneeUserIdAndTeamId_excludesOtherTeams() {
    User assignee = userRepository.save(user("assignee-filter"));
    Team otherTeam = teamRepository.save(team("Other", creator));

    Task inTeam = taskRepository.save(task(team, creator, "In team"));
    Task inOther = taskRepository.save(task(otherTeam, creator, "In other"));

    assigneeRepository.save(assignment(inTeam, assignee));
    assigneeRepository.save(assignment(inOther, assignee));

    List<Task> result =
        taskRepository.findByAssigneeUserIdAndTeamId(assignee.getId(), team.getId());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(inTeam.getId());
  }
```

Required imports (add at top of file if missing):
```java
import ua.uzhnu.collab.entity.TaskAssignee;
```
(`TaskAssignee` and `assignment()` are already available via `TestData.*` import.)

- [ ] **Step 2: Run to verify it fails**

```bash
./mvnw test -pl . -Dtest=TaskRepositoryTest#findByAssigneeUserIdAndTeamId_excludesOtherTeams -q 2>&1 | tail -20
```

Expected: `FAILED` — `findByAssigneeUserIdAndTeamId` does not exist yet.

- [ ] **Step 3: Implement the method**

In `TaskRepository.java`, add after `findByAssigneeUserId`:

```java
  public List<Task> findByAssigneeUserIdAndTeamId(Long userId, Long teamId) {
    String sql =
        SELECT
            + """
            JOIN task_assignees ta ON ta.task_id = t.id
            WHERE ta.user_id = :userId AND t.team_id = :teamId
            ORDER BY t.due_date NULLS LAST
            """;
    MapSqlParameterSource p =
        new MapSqlParameterSource().addValue("userId", userId).addValue("teamId", teamId);
    return jdbc.query(sql, p, TASK_MAPPER);
  }
```

- [ ] **Step 4: Run to verify it passes**

```bash
./mvnw test -pl . -Dtest=TaskRepositoryTest#findByAssigneeUserIdAndTeamId_excludesOtherTeams -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, `Tests run: 1, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ua/uzhnu/collab/repository/TaskRepository.java \
        src/test/java/ua/uzhnu/collab/repository/TaskRepositoryTest.java
git commit -m "feat: add TaskRepository.findByAssigneeUserIdAndTeamId for workload reports"
```

---

## Task 4: Add `TeamService.getMemberWorkloads`

**Files:**
- Modify: `src/test/java/ua/uzhnu/collab/service/TeamServiceTest.java`
- Modify: `src/main/java/ua/uzhnu/collab/service/TeamService.java`

- [ ] **Step 1: Write failing test**

Add these imports to `TeamServiceTest.java` (if not present):
```java
import java.time.LocalDateTime;
import ua.uzhnu.collab.dto.Dtos.UserWorkloadDto;
import ua.uzhnu.collab.entity.Task;
import ua.uzhnu.collab.entity.TeamMemberId;
```

Add a new `@Nested` class inside `TeamServiceTest`:

```java
  @Nested
  @DisplayName("getMemberWorkloads")
  class GetMemberWorkloads {

    @Test
    @DisplayName("повертає правильні лічильники для кожного учасника")
    void returnsCorrectCountsPerMember() {
      User memberUser =
          User.builder().id(10L).username("worker").fullName("Працівник Тест").build();
      TeamMember tm =
          TeamMember.builder()
              .id(new TeamMemberId(1L, 10L))
              .user(memberUser)
              .role(TeamRole.MEMBER)
              .build();

      Task todoTask = new Task();
      todoTask.setId(100L);
      todoTask.setStatus(TaskStatus.TODO);
      todoTask.setDueDate(null);

      Task inProgTask = new Task();
      inProgTask.setId(101L);
      inProgTask.setStatus(TaskStatus.IN_PROGRESS);
      inProgTask.setDueDate(LocalDateTime.now().minusHours(2)); // overdue

      Task doneTask = new Task();
      doneTask.setId(102L);
      doneTask.setStatus(TaskStatus.DONE);
      doneTask.setDueDate(LocalDateTime.now().minusDays(1));

      when(memberRepository.findByIdTeamId(1L)).thenReturn(List.of(tm));
      when(taskRepository.findByAssigneeUserIdAndTeamId(10L, 1L))
          .thenReturn(List.of(todoTask, inProgTask, doneTask));

      List<UserWorkloadDto> result = teamService.getMemberWorkloads(1L);

      assertThat(result).hasSize(1);
      UserWorkloadDto dto = result.get(0);
      assertThat(dto.userId()).isEqualTo(10L);
      assertThat(dto.fullName()).isEqualTo("Працівник Тест");
      assertThat(dto.todoCount()).isEqualTo(1);
      assertThat(dto.inProgressCount()).isEqualTo(1);
      assertThat(dto.doneCount()).isEqualTo(1);
      assertThat(dto.overdueCount()).isEqualTo(1);
    }
  }
```

- [ ] **Step 2: Run to verify it fails**

```bash
./mvnw test -pl . -Dtest="TeamServiceTest\$GetMemberWorkloads" -q 2>&1 | tail -20
```

Expected: `FAILED` — method `getMemberWorkloads` does not exist.

- [ ] **Step 3: Implement `getMemberWorkloads` in `TeamService`**

Add these imports to `TeamService.java` (if not present):
```java
import java.time.LocalDateTime;
import java.util.List;
```

Add the method inside `TeamService`, after `getMembers`:

```java
  /** Навантаження кожного учасника команди: кількість задач по статусах і прострочених. */
  public List<UserWorkloadDto> getMemberWorkloads(Long teamId) {
    LocalDateTime now = LocalDateTime.now();
    return memberRepository.findByIdTeamId(teamId).stream()
        .map(
            member -> {
              Long userId = member.getUser().getId();
              List<ua.uzhnu.collab.entity.Task> tasks =
                  taskRepository.findByAssigneeUserIdAndTeamId(userId, teamId);
              int todo =
                  (int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.TODO).count();
              int inProg =
                  (int)
                      tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
              int done =
                  (int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
              int overdue =
                  (int)
                      tasks.stream()
                          .filter(
                              t ->
                                  t.getStatus() != TaskStatus.DONE
                                      && t.getDueDate() != null
                                      && t.getDueDate().isBefore(now))
                          .count();
              return new UserWorkloadDto(
                  userId, member.getUser().getFullName(), 1, todo, inProg, done, overdue);
            })
        .toList();
  }
```

Add the `UserWorkloadDto` import to `TeamService.java`:
```java
import ua.uzhnu.collab.dto.Dtos.UserWorkloadDto;
```

- [ ] **Step 4: Run to verify it passes**

```bash
./mvnw test -pl . -Dtest="TeamServiceTest\$GetMemberWorkloads" -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, `Tests run: 1, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ua/uzhnu/collab/service/TeamService.java \
        src/test/java/ua/uzhnu/collab/service/TeamServiceTest.java
git commit -m "feat: add TeamService.getMemberWorkloads for workload reports"
```

---

## Task 5: Add `DialogHelper.showSaveFileDialog`

**Files:**
- Modify: `src/main/java/ua/uzhnu/collab/controller/DialogHelper.java`

- [ ] **Step 1: Add method to `DialogHelper`**

Add after `showFileChooser`:

```java
  /**
   * Діалог збереження файлу.
   *
   * @param owner вікно-власник
   * @param defaultName ім'я файлу за замовчуванням
   * @param ext розширення без крапки, наприклад "pdf" або "xlsx"
   * @return обраний файл або null при скасуванні
   */
  public File showSaveFileDialog(Window owner, String defaultName, String ext) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Зберегти файл");
    chooser.setInitialFileName(defaultName);
    String description = ext.equalsIgnoreCase("pdf") ? "PDF документ" : "Excel таблиця";
    chooser
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter(description, "*." + ext.toLowerCase()));
    return chooser.showSaveDialog(owner);
  }
```

- [ ] **Step 2: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ua/uzhnu/collab/controller/DialogHelper.java
git commit -m "feat: add DialogHelper.showSaveFileDialog for export file picker"
```

---

## Task 6: Create `ReportService` (TDD)

**Files:**
- Create: `src/test/java/ua/uzhnu/collab/service/ReportServiceTest.java`
- Create: `src/main/java/ua/uzhnu/collab/service/ReportService.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/ua/uzhnu/collab/service/ReportServiceTest.java`:

```java
package ua.uzhnu.collab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService — юніт-тест")
class ReportServiceTest {

  @Mock private TaskService taskService;
  @Mock private TeamService teamService;

  @InjectMocks private ReportService reportService;

  private static final Long TEAM_ID = 1L;

  @BeforeEach
  void setUp() {
    TeamStatsDto stats =
        new TeamStatsDto(TEAM_ID, "Тест-команда", 2, 3, 1, 1, 1, 1, 2, 2048L, 15L);
    when(teamService.getStatistics(TEAM_ID)).thenReturn(stats);

    List<TaskDto> tasks =
        List.of(
            new TaskDto(
                1L, TEAM_ID, "Тест-команда", "Задача одна", "Опис задачі",
                TaskStatus.TODO, TaskPriority.HIGH, null, "Творець",
                List.of("Іванов Іван"), 2, 0, false,
                LocalDateTime.now(), LocalDateTime.now()),
            new TaskDto(
                2L, TEAM_ID, "Тест-команда", "Задача два", null,
                TaskStatus.DONE, TaskPriority.LOW,
                LocalDateTime.now().minusDays(1), "Творець",
                List.of(), 0, 1, true,
                LocalDateTime.now(), LocalDateTime.now()));
    when(taskService.getByTeam(TEAM_ID)).thenReturn(tasks);

    List<UserWorkloadDto> workload =
        List.of(new UserWorkloadDto(10L, "Іванов Іван", 1, 1, 1, 1, 1));
    when(teamService.getMemberWorkloads(TEAM_ID)).thenReturn(workload);
  }

  // =====================================================================
  // PDF VALIDITY
  // =====================================================================

  @Test
  @DisplayName("exportTasksPdf: повертає валідний PDF з щонайменше 1 сторінкою")
  void exportTasksPdf_returnsValidPdf() throws Exception {
    byte[] bytes = reportService.exportTasksPdf(TEAM_ID);
    assertThat(bytes).isNotEmpty();
    try (PDDocument doc = Loader.loadPDF(bytes)) {
      assertThat(doc.getNumberOfPages()).isGreaterThan(0);
    }
  }

  @Test
  @DisplayName("exportStatsPdf: повертає валідний PDF")
  void exportStatsPdf_returnsValidPdf() throws Exception {
    byte[] bytes = reportService.exportStatsPdf(TEAM_ID);
    assertThat(bytes).isNotEmpty();
    try (PDDocument doc = Loader.loadPDF(bytes)) {
      assertThat(doc.getNumberOfPages()).isGreaterThan(0);
    }
  }

  @Test
  @DisplayName("exportWorkloadPdf: повертає валідний PDF")
  void exportWorkloadPdf_returnsValidPdf() throws Exception {
    byte[] bytes = reportService.exportWorkloadPdf(TEAM_ID);
    assertThat(bytes).isNotEmpty();
    try (PDDocument doc = Loader.loadPDF(bytes)) {
      assertThat(doc.getNumberOfPages()).isGreaterThan(0);
    }
  }

  // =====================================================================
  // EXCEL VALIDITY
  // =====================================================================

  @Test
  @DisplayName("exportTasksExcel: повертає валідний workbook з title+header+2 рядки")
  void exportTasksExcel_returnsValidWorkbook() throws Exception {
    byte[] bytes = reportService.exportTasksExcel(TEAM_ID);
    assertThat(bytes).isNotEmpty();
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = wb.getSheetAt(0);
      // row 0 = title, row 1 = headers, row 2+3 = 2 tasks
      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(4);
    }
  }

  @Test
  @DisplayName("exportStatsExcel: повертає валідний workbook з title+header+8 рядків статистики")
  void exportStatsExcel_returnsValidWorkbook() throws Exception {
    byte[] bytes = reportService.exportStatsExcel(TEAM_ID);
    assertThat(bytes).isNotEmpty();
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = wb.getSheetAt(0);
      // row 0 = title, row 1 = headers, rows 2-9 = 8 stats
      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(10);
    }
  }

  @Test
  @DisplayName("exportWorkloadExcel: повертає валідний workbook з title+header+1 учасник")
  void exportWorkloadExcel_returnsValidWorkbook() throws Exception {
    byte[] bytes = reportService.exportWorkloadExcel(TEAM_ID);
    assertThat(bytes).isNotEmpty();
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = wb.getSheetAt(0);
      // row 0 = title, row 1 = headers, row 2 = 1 member
      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
    }
  }
}
```

- [ ] **Step 2: Run to verify all 6 tests fail**

```bash
./mvnw test -pl . -Dtest=ReportServiceTest -q 2>&1 | tail -20
```

Expected: `FAILED` — `ReportService` class does not exist yet.

- [ ] **Step 3: Implement `ReportService`**

Create `src/main/java/ua/uzhnu/collab/service/ReportService.java`:

```java
package ua.uzhnu.collab.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.enums.TaskPriority;
import ua.uzhnu.collab.enums.TaskStatus;

/**
 * Генерує звіти у форматах PDF та Excel.
 *
 * <p>Три звіти × два формати = шість публічних методів. Дані отримуються через існуючі сервіси;
 * цей клас не звертається до БД напряму.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportService {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
  private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final float MARGIN = 36f;
  private static final float ROW_H = 18f;
  private static final float FONT_SIZE = 9f;
  private static final String FONT_REGULAR = "/fonts/DejaVuSans.ttf";
  private static final String FONT_BOLD = "/fonts/DejaVuSans-Bold.ttf";

  private final TaskService taskService;
  private final TeamService teamService;

  // =====================================================================
  // PUBLIC — PDF
  // =====================================================================

  public byte[] exportTasksPdf(Long teamId) {
    String teamName = teamService.getStatistics(teamId).teamName();
    List<TaskDto> tasks = taskService.getByTeam(teamId);
    String[] headers = {"Назва", "Статус", "Пріоритет", "Виконавці", "Дедлайн", "Прострочено"};
    float[] widths = {230f, 70f, 80f, 140f, 110f, 80f};
    String[][] rows =
        tasks.stream()
            .map(
                t ->
                    new String[] {
                      t.title(),
                      fmtStatus(t.status()),
                      fmtPriority(t.priority()),
                      String.join(", ", t.assigneeNames()),
                      t.dueDate() != null ? t.dueDate().format(DATE_FMT) : "—",
                      t.overdue() ? "Так" : "Ні"
                    })
            .toArray(String[][]::new);
    return buildPdf("Звіт по задачах", teamName, new PDRectangle(PDRectangle.A4.getHeight(),
        PDRectangle.A4.getWidth()), headers, widths, rows);
  }

  public byte[] exportStatsPdf(Long teamId) {
    TeamStatsDto s = teamService.getStatistics(teamId);
    String[] headers = {"Показник", "Значення"};
    float[] widths = {220f, 120f};
    String[][] rows = {
      {"Учасників", String.valueOf(s.memberCount())},
      {"Задач всього", String.valueOf(s.totalTasks())},
      {"TODO", String.valueOf(s.todoCount())},
      {"В роботі", String.valueOf(s.inProgressCount())},
      {"Виконано", String.valueOf(s.doneCount())},
      {"Прострочено", String.valueOf(s.overdueCount())},
      {"Файлів", String.valueOf(s.fileCount())},
      {"Повідомлень у чаті", String.valueOf(s.messageCount())}
    };
    return buildPdf("Статистика команди", s.teamName(), PDRectangle.A4, headers, widths, rows);
  }

  public byte[] exportWorkloadPdf(Long teamId) {
    String teamName = teamService.getStatistics(teamId).teamName();
    List<UserWorkloadDto> workload = teamService.getMemberWorkloads(teamId);
    String[] headers = {"Учасник", "TODO", "В роботі", "Виконано", "Прострочено"};
    float[] widths = {180f, 55f, 70f, 70f, 90f};
    String[][] rows =
        workload.stream()
            .map(
                w ->
                    new String[] {
                      w.fullName(),
                      String.valueOf(w.todoCount()),
                      String.valueOf(w.inProgressCount()),
                      String.valueOf(w.doneCount()),
                      String.valueOf(w.overdueCount())
                    })
            .toArray(String[][]::new);
    return buildPdf("Навантаження учасників", teamName, PDRectangle.A4, headers, widths, rows);
  }

  // =====================================================================
  // PUBLIC — EXCEL
  // =====================================================================

  public byte[] exportTasksExcel(Long teamId) {
    String teamName = teamService.getStatistics(teamId).teamName();
    List<TaskDto> tasks = taskService.getByTeam(teamId);
    String[] headers = {"Назва", "Статус", "Пріоритет", "Виконавці", "Дедлайн", "Прострочено"};
    String[][] rows =
        tasks.stream()
            .map(
                t ->
                    new String[] {
                      t.title(),
                      fmtStatus(t.status()),
                      fmtPriority(t.priority()),
                      String.join(", ", t.assigneeNames()),
                      t.dueDate() != null ? t.dueDate().format(DATE_FMT) : "",
                      t.overdue() ? "Так" : "Ні"
                    })
            .toArray(String[][]::new);
    return buildExcel("Задачі", teamName + " — Звіт по задачах", headers, rows);
  }

  public byte[] exportStatsExcel(Long teamId) {
    TeamStatsDto s = teamService.getStatistics(teamId);
    String[] headers = {"Показник", "Значення"};
    String[][] rows = {
      {"Учасників", String.valueOf(s.memberCount())},
      {"Задач всього", String.valueOf(s.totalTasks())},
      {"TODO", String.valueOf(s.todoCount())},
      {"В роботі", String.valueOf(s.inProgressCount())},
      {"Виконано", String.valueOf(s.doneCount())},
      {"Прострочено", String.valueOf(s.overdueCount())},
      {"Файлів", String.valueOf(s.fileCount())},
      {"Повідомлень у чаті", String.valueOf(s.messageCount())}
    };
    return buildExcel("Статистика", s.teamName() + " — Статистика команди", headers, rows);
  }

  public byte[] exportWorkloadExcel(Long teamId) {
    String teamName = teamService.getStatistics(teamId).teamName();
    List<UserWorkloadDto> workload = teamService.getMemberWorkloads(teamId);
    String[] headers = {"Учасник", "TODO", "В роботі", "Виконано", "Прострочено"};
    String[][] rows =
        workload.stream()
            .map(
                w ->
                    new String[] {
                      w.fullName(),
                      String.valueOf(w.todoCount()),
                      String.valueOf(w.inProgressCount()),
                      String.valueOf(w.doneCount()),
                      String.valueOf(w.overdueCount())
                    })
            .toArray(String[][]::new);
    return buildExcel(
        "Навантаження", teamName + " — Навантаження учасників", headers, rows);
  }

  // =====================================================================
  // PRIVATE — PDF BUILDER
  // =====================================================================

  private byte[] buildPdf(
      String reportType,
      String teamName,
      PDRectangle pageSize,
      String[] headers,
      float[] colWidths,
      String[][] rows) {
    try (PDDocument doc = new PDDocument()) {
      PDFont bold = loadFont(doc, FONT_BOLD);
      PDFont normal = loadFont(doc, FONT_REGULAR);

      PDPage page = new PDPage(pageSize);
      doc.addPage(page);
      float pageH = pageSize.getHeight();

      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(bold, 13f);
        cs.newLineAtOffset(MARGIN, pageH - MARGIN);
        cs.showText(teamName + " — " + reportType);
        cs.endText();

        cs.beginText();
        cs.setFont(normal, 8f);
        cs.newLineAtOffset(MARGIN, pageH - MARGIN - 14f);
        cs.showText("Згенеровано: " + LocalDate.now().format(DATE_SHORT));
        cs.endText();

        drawTable(cs, MARGIN, pageH - MARGIN - 36f, colWidths, headers, rows, bold, normal);
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Помилка генерації PDF: " + e.getMessage(), e);
    }
  }

  private PDFont loadFont(PDDocument doc, String resourcePath) throws IOException {
    try (InputStream in =
        Objects.requireNonNull(
            ReportService.class.getResourceAsStream(resourcePath),
            "Font not found: " + resourcePath)) {
      return PDType0Font.load(doc, in);
    }
  }

  private void drawTable(
      PDPageContentStream cs,
      float startX,
      float startY,
      float[] colWidths,
      String[] headers,
      String[][] rows,
      PDFont bold,
      PDFont normal)
      throws IOException {
    float tableW = 0f;
    for (float w : colWidths) tableW += w;

    float y = startY;
    drawRow(cs, startX, y, colWidths, headers, bold);
    y -= ROW_H;

    cs.setLineWidth(0.8f);
    cs.moveTo(startX, y);
    cs.lineTo(startX + tableW, y);
    cs.stroke();

    for (String[] row : rows) {
      drawRow(cs, startX, y, colWidths, row, normal);
      y -= ROW_H;
    }

    cs.setLineWidth(1f);
    cs.addRect(startX, y, tableW, startY - y);
    cs.stroke();

    float colX = startX;
    for (int i = 0; i < colWidths.length - 1; i++) {
      colX += colWidths[i];
      cs.setLineWidth(0.3f);
      cs.moveTo(colX, startY);
      cs.lineTo(colX, y);
      cs.stroke();
    }
  }

  private void drawRow(
      PDPageContentStream cs, float x, float y, float[] colWidths, String[] cells, PDFont font)
      throws IOException {
    float colX = x + 3f;
    for (int i = 0; i < cells.length && i < colWidths.length; i++) {
      String text = cells[i] != null ? cells[i] : "";
      text = clipText(text, font, FONT_SIZE, colWidths[i] - 6f);
      cs.beginText();
      cs.setFont(font, FONT_SIZE);
      cs.newLineAtOffset(colX, y - ROW_H + 5f);
      cs.showText(text);
      cs.endText();
      colX += colWidths[i];
    }
  }

  private String clipText(String text, PDFont font, float fontSize, float maxWidth)
      throws IOException {
    if (text.isEmpty()) return text;
    while (text.length() > 1 && font.getStringWidth(text) / 1000f * fontSize > maxWidth) {
      text = text.substring(0, text.length() - 1);
    }
    return text;
  }

  // =====================================================================
  // PRIVATE — EXCEL BUILDER
  // =====================================================================

  private byte[] buildExcel(String sheetName, String reportTitle, String[] headers, String[][] rows) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      XSSFSheet sheet = wb.createSheet(sheetName);

      XSSFCellStyle titleStyle = makeTitleStyle(wb);
      XSSFCellStyle headerStyle = makeHeaderStyle(wb);

      Row titleRow = sheet.createRow(0);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue(reportTitle);
      titleCell.setCellStyle(titleStyle);
      sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

      Row headerRow = sheet.createRow(1);
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      for (int r = 0; r < rows.length; r++) {
        Row row = sheet.createRow(r + 2);
        for (int c = 0; c < rows[r].length; c++) {
          row.createCell(c).setCellValue(rows[r][c] != null ? rows[r][c] : "");
        }
      }

      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
      }
      sheet.createFreezePane(0, 2);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      wb.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Помилка генерації Excel: " + e.getMessage(), e);
    }
  }

  private XSSFCellStyle makeTitleStyle(XSSFWorkbook wb) {
    XSSFCellStyle style = wb.createCellStyle();
    style.setFillForegroundColor(
        new XSSFColor(new byte[] {(byte) 41, (byte) 128, (byte) 185}, null));
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    XSSFFont font = wb.createFont();
    font.setBold(true);
    font.setColor(IndexedColors.WHITE.getIndex());
    font.setFontHeightInPoints((short) 12);
    style.setFont(font);
    return style;
  }

  private XSSFCellStyle makeHeaderStyle(XSSFWorkbook wb) {
    XSSFCellStyle style = wb.createCellStyle();
    style.setFillForegroundColor(
        new XSSFColor(new byte[] {(byte) 52, (byte) 73, (byte) 94}, null));
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    XSSFFont font = wb.createFont();
    font.setBold(true);
    font.setColor(IndexedColors.WHITE.getIndex());
    style.setFont(font);
    return style;
  }

  // =====================================================================
  // PRIVATE — FORMATTERS
  // =====================================================================

  private String fmtStatus(TaskStatus s) {
    return switch (s) {
      case TODO -> "TODO";
      case IN_PROGRESS -> "В роботі";
      case DONE -> "Виконано";
    };
  }

  private String fmtPriority(TaskPriority p) {
    return switch (p) {
      case CRITICAL -> "Критичний";
      case HIGH -> "Високий";
      case MEDIUM -> "Середній";
      case LOW -> "Низький";
    };
  }
}
```

- [ ] **Step 4: Run to verify all 6 tests pass**

```bash
./mvnw test -pl . -Dtest=ReportServiceTest -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`, `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ua/uzhnu/collab/service/ReportService.java \
        src/test/java/ua/uzhnu/collab/service/ReportServiceTest.java
git commit -m "feat: add ReportService with PDF and Excel export for 3 report types"
```

---

## Task 7: Wire UI — FXML + Controller

**Files:**
- Modify: `src/main/resources/fxml/team_view_v2.fxml`
- Modify: `src/main/java/ua/uzhnu/collab/controller/TeamViewControllerV2.java`

- [ ] **Step 1: Add `MenuButton` to `team_view_v2.fxml`**

In `team_view_v2.fxml`, find the toolbar `HBox` (line ~19). Add `MenuButton` **before** `ProgressIndicator`:

Replace:
```xml
                <Button text="Оновити" onAction="#handleRefresh" styleClass="btn-secondary"/>
                <ProgressIndicator fx:id="spinner" visible="false" prefWidth="24" prefHeight="24"/>
```

With:
```xml
                <Button text="Оновити" onAction="#handleRefresh" styleClass="btn-secondary"/>
                <MenuButton text="Експорт ▾" styleClass="btn-secondary">
                    <items>
                        <MenuItem text="Задачі → PDF" onAction="#handleExportTasksPdf"/>
                        <MenuItem text="Задачі → Excel" onAction="#handleExportTasksExcel"/>
                        <SeparatorMenuItem/>
                        <MenuItem text="Статистика → PDF" onAction="#handleExportStatsPdf"/>
                        <MenuItem text="Статистика → Excel" onAction="#handleExportStatsExcel"/>
                        <SeparatorMenuItem/>
                        <MenuItem text="Навантаження → PDF" onAction="#handleExportWorkloadPdf"/>
                        <MenuItem text="Навантаження → Excel" onAction="#handleExportWorkloadExcel"/>
                    </items>
                </MenuButton>
                <ProgressIndicator fx:id="spinner" visible="false" prefWidth="24" prefHeight="24"/>
```

- [ ] **Step 2: Add `ReportService` dependency and imports to `TeamViewControllerV2`**

At the top of `TeamViewControllerV2.java`, add these imports:

```java
import java.io.File;
import java.util.function.Supplier;
import ua.uzhnu.collab.service.ReportService;
```

In the class body, add `reportService` to the existing `@RequiredArgsConstructor` fields (add after `session`):

```java
  private final ReportService reportService;
```

- [ ] **Step 3: Add `runExport` helper + 6 handler methods**

Add at the end of `TeamViewControllerV2`, before the closing `}`:

```java
  // =====================================================================
  // ЕКСПОРТ
  // =====================================================================

  @FXML
  private void handleExportTasksPdf() {
    Long teamId = viewModel.teamProperty().get().id();
    runExport(
        () -> reportService.exportTasksPdf(teamId),
        "tasks_report.pdf",
        "pdf");
  }

  @FXML
  private void handleExportTasksExcel() {
    Long teamId = viewModel.teamProperty().get().id();
    runExport(
        () -> reportService.exportTasksExcel(teamId),
        "tasks_report.xlsx",
        "xlsx");
  }

  @FXML
  private void handleExportStatsPdf() {
    Long teamId = viewModel.teamProperty().get().id();
    runExport(
        () -> reportService.exportStatsPdf(teamId),
        "statistics_report.pdf",
        "pdf");
  }

  @FXML
  private void handleExportStatsExcel() {
    Long teamId = viewModel.teamProperty().get().id();
    runExport(
        () -> reportService.exportStatsExcel(teamId),
        "statistics_report.xlsx",
        "xlsx");
  }

  @FXML
  private void handleExportWorkloadPdf() {
    Long teamId = viewModel.teamProperty().get().id();
    runExport(
        () -> reportService.exportWorkloadPdf(teamId),
        "workload_report.pdf",
        "pdf");
  }

  @FXML
  private void handleExportWorkloadExcel() {
    Long teamId = viewModel.teamProperty().get().id();
    runExport(
        () -> reportService.exportWorkloadExcel(teamId),
        "workload_report.xlsx",
        "xlsx");
  }

  private void runExport(Supplier<byte[]> generator, String defaultName, String ext) {
    viewModel.loadingProperty().set(true);
    javafx.concurrent.Task<byte[]> task =
        new javafx.concurrent.Task<>() {
          @Override
          protected byte[] call() {
            return generator.get();
          }
        };
    task.setOnSucceeded(
        e -> {
          viewModel.loadingProperty().set(false);
          File file =
              dialogs.showSaveFileDialog(lblTeamName.getScene().getWindow(), defaultName, ext);
          if (file == null) return;
          try {
            java.nio.file.Files.write(file.toPath(), task.getValue());
            dialogs.showInfo("Збережено", "Файл збережено: " + file.getName());
          } catch (Exception ex) {
            dialogs.showError("Помилка запису файлу", ex.getMessage());
          }
        });
    task.setOnFailed(
        e -> {
          viewModel.loadingProperty().set(false);
          dialogs.showError("Помилка експорту", task.getException().getMessage());
        });
    new Thread(task).start();
  }
```

- [ ] **Step 4: Verify full build + all tests pass**

```bash
./mvnw test -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, all tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/fxml/team_view_v2.fxml \
        src/main/java/ua/uzhnu/collab/controller/TeamViewControllerV2.java
git commit -m "feat: add export MenuButton and handlers to TeamViewControllerV2"
```

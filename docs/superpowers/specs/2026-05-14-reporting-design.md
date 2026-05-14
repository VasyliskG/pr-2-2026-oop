# Reporting & Export — Design Spec
**Date:** 2026-05-14  
**Project:** Student Collab Platform  
**Status:** Approved

---

## 1. Goal

Add PDF and Excel export for three team reports in `TeamViewControllerV2`. Generation is async (JavaFX `Task`), saving via `FileChooser`. No new SQL queries — reuse existing services.

---

## 2. Reports

| Report | Data source | Columns |
|--------|-------------|---------|
| Team tasks | `taskService.getByTeam(teamId)` | Title, Status, Priority, Assignees, Due date, Overdue |
| Team statistics | `teamService.getStats(teamId)` | Members, Total tasks, TODO, In progress, Done, Overdue, Files |
| Member workload | `teamService.getMemberWorkloads(teamId)` | Full name, TODO, In progress, Done, Overdue |

`getMemberWorkloads(teamId)` is a **new method** to add to `TeamService` — aggregates `UserWorkloadDto` for all team members.

---

## 3. New Components

### 3.1 Dependencies (pom.xml)

```xml
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

### 3.2 `ReportService` (`ua.uzhnu.collab.service`)

Spring `@Service`, `@Transactional(readOnly = true)`. Six public methods:

```java
byte[] exportTasksPdf(Long teamId)
byte[] exportTasksExcel(Long teamId)
byte[] exportStatsPdf(Long teamId)
byte[] exportStatsExcel(Long teamId)
byte[] exportWorkloadPdf(Long teamId)
byte[] exportWorkloadExcel(Long teamId)
```

All return `byte[]` written to `ByteArrayOutputStream`. Shared private helpers:

- `drawTable(PDPageContentStream, String[] headers, String[][] rows)` — draws table using PDFBox lines + text cells
- `createHeaderStyle(Workbook)` — POI cell style: blue fill, white bold text
- `createOverdueStyle(Workbook)` — POI cell style: red text for overdue tasks

Dependencies injected: `TaskService`, `TeamService`.

AOP `LoggingAspect` intercepts automatically (covers all `@Service` methods already).

### 3.3 `TeamService.getMemberWorkloads(Long teamId)`

New method. Fetches all team members, then for each member queries `taskRepository.findByAssigneeUserId(userId)` filtered to this team, groups by status, counts overdue. Returns `List<UserWorkloadDto>`.

### 3.4 `DialogHelper.showSaveFileDialog(Window, String defaultName, String ext)`

New method. Wraps JavaFX `FileChooser` with `ExtensionFilter` for `.pdf` or `.xlsx`. Returns `File` or `null` if cancelled.

---

## 4. UI Changes

### 4.1 `team_view_v2.fxml`

Add `MenuButton` labelled "Експорт ▾" in the toolbar area. Six `MenuItem` children:

```
Задачі → PDF
Задачі → Excel
Статистика → PDF
Статистика → Excel
Навантаження → PDF
Навантаження → Excel
```

### 4.2 `TeamViewControllerV2`

Inject `ReportService`. Add handler method per menu item, all following the same async pattern:

```java
private void handleExport(Supplier<byte[]> generator, String defaultName, String ext) {
    viewModel.loadingProperty().set(true);
    javafx.concurrent.Task<byte[]> task = new Task<>() {
        @Override protected byte[] call() { return generator.get(); }
    };
    task.setOnSucceeded(e -> {
        viewModel.loadingProperty().set(false);
        File file = dialogs.showSaveFileDialog(lblTeamName.getScene().getWindow(), defaultName, ext);
        if (file == null) return;
        try {
            Files.write(file.toPath(), task.getValue());
            dialogs.showInfo("Збережено", "Файл збережено: " + file.getName());
        } catch (Exception ex) {
            dialogs.showError("Помилка запису", ex.getMessage());
        }
    });
    task.setOnFailed(e -> {
        viewModel.loadingProperty().set(false);
        dialogs.showError("Помилка експорту", task.getException().getMessage());
    });
    new Thread(task).start();
}
```

Existing `spinner` (bound to `viewModel.loadingProperty()`) shows during generation.

---

## 5. PDF Formatting

Each PDF report:
- **Header row:** Team name + report type + generation date (right-aligned)
- **Table:** Drawn via `drawTable()` helper — horizontal/vertical lines, text clipped to cell width
- **Footer:** "Згенеровано: StudentCollab Platform"

Font: PDType1Font.HELVETICA for body, PDType1Font.HELVETICA_BOLD for headers. Page size: A4 landscape for task list (many columns), A4 portrait for stats and workload.

---

## 6. Excel Formatting

```
XSSFWorkbook
└── Sheet (named per report type)
    ├── Row 0: report title (merged cells, bold, blue fill)
    ├── Row 1: column headers (bold, blue fill, white text)
    └── Row 2+: data rows
```

- Overdue tasks: red text (`IndexedColors.RED`)
- `sheet.autoSizeColumn(i)` for all columns
- `sheet.createFreezePane(0, 2)` to freeze header rows

---

## 7. Testing

`ReportServiceTest` — Spring integration test (`@SpringBootTest`), uses existing test data.

| Test | Assertion |
|------|-----------|
| `exportTasksPdf` returns valid PDF | `PDDocument.load(bytes)` no exception, page count ≥ 1 |
| `exportTasksExcel` returns valid workbook | `WorkbookFactory.create(stream)` no exception, sheet row count = task count + 2 |
| `exportStatsPdf` returns valid PDF | bytes.length > 0, loads without exception |
| `exportStatsExcel` returns valid workbook | header row columns match expected names |
| `exportWorkloadPdf` returns valid PDF | bytes.length > 0 |
| `exportWorkloadExcel` returns valid workbook | row count = member count + 2 |

---

## 8. Out of Scope

- Charts/graphs in reports
- Report scheduling or email delivery
- Multi-team aggregate reports (Dashboard-level)
- Print preview UI

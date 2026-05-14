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
    return buildPdf(
        "Звіт по задачах",
        teamName,
        new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()),
        headers,
        widths,
        rows);
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
    List<TaskDto> taskList = tasks;
    boolean[] overdueFlags = new boolean[taskList.size()];
    for (int i = 0; i < taskList.size(); i++) {
      overdueFlags[i] = taskList.get(i).overdue();
    }
    return buildExcel("Задачі", teamName + " — Звіт по задачах", headers, rows, overdueFlags);
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
    return buildExcel("Статистика", s.teamName() + " — Статистика команди", headers, rows, null);
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
    return buildExcel("Навантаження", teamName + " — Навантаження учасників", headers, rows, null);
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

        cs.beginText();
        cs.setFont(normal, 8f);
        cs.newLineAtOffset(MARGIN, MARGIN);
        cs.showText("Згенеровано: StudentCollab Platform");
        cs.endText();
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

  private byte[] buildExcel(
      String sheetName, String reportTitle, String[] headers, String[][] rows,
      boolean[] overdueFlags) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      XSSFSheet sheet = wb.createSheet(sheetName);

      XSSFCellStyle titleStyle = makeTitleStyle(wb);
      XSSFCellStyle headerStyle = makeHeaderStyle(wb);
      XSSFCellStyle overdueStyle = createOverdueStyle(wb);

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
        boolean isOverdue = overdueFlags != null && overdueFlags[r];
        for (int c = 0; c < rows[r].length; c++) {
          Cell cell = row.createCell(c);
          cell.setCellValue(rows[r][c] != null ? rows[r][c] : "");
          if (isOverdue) {
            cell.setCellStyle(overdueStyle);
          }
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

  private XSSFCellStyle createOverdueStyle(XSSFWorkbook wb) {
    XSSFCellStyle style = wb.createCellStyle();
    XSSFFont font = wb.createFont();
    font.setColor(IndexedColors.RED.getIndex());
    style.setFont(font);
    return style;
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

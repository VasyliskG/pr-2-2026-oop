package ua.uzhnu.collab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
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
    lenient().when(teamService.getStatistics(TEAM_ID)).thenReturn(stats);

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
    lenient().when(taskService.getByTeam(TEAM_ID)).thenReturn(tasks);

    List<UserWorkloadDto> workload =
        List.of(new UserWorkloadDto(10L, "Іванов Іван", 1, 1, 1, 1, 1));
    lenient().when(teamService.getMemberWorkloads(TEAM_ID)).thenReturn(workload);
  }

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

  @Test
  @DisplayName("exportTasksExcel: повертає валідний workbook з title+header+2 рядки")
  void exportTasksExcel_returnsValidWorkbook() throws Exception {
    byte[] bytes = reportService.exportTasksExcel(TEAM_ID);
    assertThat(bytes).isNotEmpty();
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = wb.getSheetAt(0);
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
      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
    }
  }
}

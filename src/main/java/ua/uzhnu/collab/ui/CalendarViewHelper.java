package ua.uzhnu.collab.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import ua.uzhnu.collab.dto.Dtos.TaskDto;

/**
 * Shared helpers for the Google-like deadline calendar views.
 */
public final class CalendarViewHelper {

  public enum CalendarMode {
    MONTH,
    WEEK,
    LIST
  }

  public record DateRange(LocalDate from, LocalDate to) {}

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  private CalendarViewHelper() {}

  public static DateRange rangeFor(LocalDate anchor, CalendarMode mode) {
    LocalDate safeAnchor = anchor == null ? LocalDate.now() : anchor;
    return switch (mode) {
      case MONTH -> new DateRange(safeAnchor.withDayOfMonth(1), safeAnchor.withDayOfMonth(safeAnchor.lengthOfMonth()));
      case WEEK -> {
        LocalDate start = safeAnchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        yield new DateRange(start, start.plusDays(6));
      }
      case LIST -> new DateRange(safeAnchor, safeAnchor.plusDays(29));
    };
  }

  public static LocalDate shiftAnchor(LocalDate anchor, CalendarMode mode, int direction) {
    LocalDate safeAnchor = anchor == null ? LocalDate.now() : anchor;
    return switch (mode) {
      case MONTH -> safeAnchor.plusMonths(direction);
      case WEEK -> safeAnchor.plusWeeks(direction);
      case LIST -> safeAnchor.plusDays(direction * 30L);
    };
  }

  public static String formatRange(DateRange range) {
    if (range == null) {
      return "";
    }
    return DATE_FORMAT.format(range.from()) + " — " + DATE_FORMAT.format(range.to());
  }

  public static List<TaskDto> sortTasks(List<TaskDto> tasks) {
    if (tasks == null || tasks.isEmpty()) {
      return List.of();
    }
    return tasks.stream()
        .filter(Objects::nonNull)
        .sorted(
            Comparator.comparing(TaskDto::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TaskDto::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
        .toList();
  }

  public static void configureUniformGrid(GridPane grid, int columns, int rows) {
    if (grid == null) {
      return;
    }
    grid.getColumnConstraints().clear();
    grid.getRowConstraints().clear();
    grid.setHgap(8);
    grid.setVgap(8);
    grid.setPadding(new Insets(8));
    grid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    for (int i = 0; i < columns; i++) {
      ColumnConstraints cc = new ColumnConstraints();
      cc.setPercentWidth(100.0 / columns);
      cc.setHgrow(Priority.ALWAYS);
      grid.getColumnConstraints().add(cc);
    }
    for (int i = 0; i < rows; i++) {
      RowConstraints rc = new RowConstraints();
      rc.setPercentHeight(100.0 / rows);
      rc.setVgrow(Priority.ALWAYS);
      grid.getRowConstraints().add(rc);
    }
  }

  public static void renderMonth(
      GridPane grid,
      LocalDate anchor,
      List<TaskDto> tasks,
      Function<TaskDto, String> taskLabelFormatter,
      Consumer<TaskDto> onTaskClick,
      Consumer<LocalDate> onDateClick) {
    if (grid == null) {
      return;
    }

    LocalDate safeAnchor = anchor == null ? LocalDate.now() : anchor;
    YearMonth month = YearMonth.from(safeAnchor);
    LocalDate firstOfMonth = month.atDay(1);
    LocalDate lastOfMonth = month.atEndOfMonth();
    LocalDate start = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate end = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

    Map<LocalDate, List<TaskDto>> byDay = groupByDate(tasks);
    List<LocalDate> visibleDays = new ArrayList<>();
    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
      visibleDays.add(date);
    }

    configureUniformGrid(grid, 7, Math.max(1, visibleDays.size() / 7));
    grid.getChildren().clear();

    for (int index = 0; index < visibleDays.size(); index++) {
      LocalDate day = visibleDays.get(index);
      int column = index % 7;
      int row = index / 7;
      grid.getChildren()
          .add(buildDayCell(day, day.getMonth().equals(month.getMonth()), byDay.get(day), taskLabelFormatter, onTaskClick, onDateClick));
      GridPane.setColumnIndex(grid.getChildren().get(grid.getChildren().size() - 1), column);
      GridPane.setRowIndex(grid.getChildren().get(grid.getChildren().size() - 1), row);
    }
  }

  public static void renderWeek(
      GridPane grid,
      LocalDate anchor,
      List<TaskDto> tasks,
      Function<TaskDto, String> taskLabelFormatter,
      Consumer<TaskDto> onTaskClick,
      Consumer<LocalDate> onDateClick) {
    if (grid == null) {
      return;
    }

    LocalDate safeAnchor = anchor == null ? LocalDate.now() : anchor;
    LocalDate start = safeAnchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    Map<LocalDate, List<TaskDto>> byDay = groupByDate(tasks);

    configureUniformGrid(grid, 7, 1);
    grid.getChildren().clear();

    for (int i = 0; i < 7; i++) {
      LocalDate day = start.plusDays(i);
      Node cell = buildDayCell(day, true, byDay.get(day), taskLabelFormatter, onTaskClick, onDateClick, true);
      grid.getChildren().add(cell);
      GridPane.setColumnIndex(cell, i);
      GridPane.setRowIndex(cell, 0);
    }
  }

  public static void installAgendaCellFactory(
      ListView<TaskDto> list,
      Function<TaskDto, String> itemFormatter,
      Consumer<TaskDto> onTaskClick) {
    if (list == null) {
      return;
    }
    list.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(TaskDto task, boolean empty) {
                super.updateItem(task, empty);
                if (empty || task == null) {
                  setText(null);
                  setOnMouseClicked(null);
                  return;
                }
                setText(itemFormatter.apply(task));
                setOnMouseClicked(
                    e -> {
                      if (e.getClickCount() == 2 && onTaskClick != null) {
                        onTaskClick.accept(task);
                      }
                    });
              }
            });
  }

  public static List<TaskDto> normalizedTasks(List<TaskDto> tasks) {
    return sortTasks(tasks);
  }

  private static Map<LocalDate, List<TaskDto>> groupByDate(List<TaskDto> tasks) {
    if (tasks == null || tasks.isEmpty()) {
      return Map.of();
    }
    return tasks.stream()
        .filter(Objects::nonNull)
        .filter(t -> t.dueDate() != null)
        .collect(Collectors.groupingBy(t -> t.dueDate().toLocalDate(), HashMap::new, Collectors.toList()));
  }

  private static Node buildDayCell(
      LocalDate day,
      boolean inVisibleRange,
      List<TaskDto> tasks,
      Function<TaskDto, String> taskLabelFormatter,
      Consumer<TaskDto> onTaskClick,
      Consumer<LocalDate> onDateClick) {
    return buildDayCell(day, inVisibleRange, tasks, taskLabelFormatter, onTaskClick, onDateClick, false);
  }

  private static Node buildDayCell(
      LocalDate day,
      boolean inVisibleRange,
      List<TaskDto> tasks,
      Function<TaskDto, String> taskLabelFormatter,
      Consumer<TaskDto> onTaskClick,
      Consumer<LocalDate> onDateClick,
      boolean weekMode) {
    VBox cell = new VBox(6);
    cell.getStyleClass().add("calendar-day-cell");
    if (!inVisibleRange) {
      cell.getStyleClass().add("calendar-day-cell-outside");
    }
    if (day.equals(LocalDate.now())) {
      cell.getStyleClass().add("calendar-day-cell-today");
    }
    if (day.equals(LocalDate.now())) {
      cell.getStyleClass().add("calendar-day-cell-selected");
    }
    cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    cell.setPadding(new Insets(10));

    Label header = new Label();
    header.setWrapText(false);
    header.getStyleClass().add("calendar-day-number");
    header.setText(weekMode ? day.getDayOfWeek().name().substring(0, 3) + " · " + day.getDayOfMonth() : String.valueOf(day.getDayOfMonth()));
    cell.getChildren().add(header);

    List<TaskDto> dayTasks = sortTasks(tasks);
    int limit = weekMode ? 6 : 3;
    for (int i = 0; i < Math.min(limit, dayTasks.size()); i++) {
      TaskDto task = dayTasks.get(i);
      Label taskLabel = new Label(taskLabelFormatter.apply(task));
      taskLabel.setWrapText(true);
      taskLabel.setMaxWidth(Double.MAX_VALUE);
      taskLabel.getStyleClass().add("calendar-task-chip");
      if (onTaskClick != null) {
        taskLabel.setOnMouseClicked(
            e -> {
              if (e.getClickCount() >= 1) {
                onTaskClick.accept(task);
              }
            });
      }
      cell.getChildren().add(taskLabel);
    }

    if (dayTasks.size() > limit) {
      Label more = new Label("+" + (dayTasks.size() - limit) + " ще");
      more.getStyleClass().add("calendar-task-more");
      cell.getChildren().add(more);
    }

    if (onDateClick != null) {
      cell.setOnMouseClicked(
          e -> {
            if (e.getClickCount() == 2) {
              onDateClick.accept(day);
            }
          });
    }

    return cell;
  }
}


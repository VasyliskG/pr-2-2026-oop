package ua.uzhnu.collab.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.uzhnu.collab.TestData.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ua.uzhnu.collab.AbstractRepositoryIntegrationTest;
import ua.uzhnu.collab.entity.*;

@DisplayName("AppFileRepository — інтеграційний тест")
class AppFileRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private AppFileRepository fileRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private TaskRepository taskRepository;

  private User uploader;
  private Team team;
  private Task task;

  @BeforeEach
  void setUp() {
    uploader = userRepository.save(user("uploader"));
    team = teamRepository.save(team("Team", uploader));
    task = taskRepository.save(task(team, uploader, "Task"));
  }

  @Test
  @DisplayName("save: створює файл з прив'язкою до задачі")
  void save_withTask_persistsTaskFK() {
    AppFile f = file(team, uploader, "doc.pdf", 1024);
    f.setTask(task);

    AppFile saved = fileRepository.save(f);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getTask().getId()).isEqualTo(task.getId());
    assertThat(saved.getUploadedAt()).isNotNull();
  }

  @Test
  @DisplayName("save: файл без прив'язки до задачі (task IS NULL)")
  void save_withoutTask_taskIsNull() {
    AppFile saved = fileRepository.save(file(team, uploader, "shared.txt", 2048));

    assertThat(saved.getTask()).isNull();
  }

  @Test
  @DisplayName("findByTeamIdOrderByUploadedAtDesc: усі файли команди, DESC за датою")
  void findByTeamIdOrderByUploadedAtDesc_works() throws InterruptedException {
    AppFile f1 = fileRepository.save(file(team, uploader, "f1.txt", 100));
    Thread.sleep(5);
    AppFile f2 = fileRepository.save(file(team, uploader, "f2.txt", 200));

    List<AppFile> files = fileRepository.findByTeamIdOrderByUploadedAtDesc(team.getId());

    assertThat(files).extracting(AppFile::getId).containsExactly(f2.getId(), f1.getId());
  }

  @Test
  @DisplayName("findByTaskId: лише файли прив'язані до задачі")
  void findByTaskId_returnsTaskFiles() {
    AppFile attached = file(team, uploader, "doc.pdf", 100);
    attached.setTask(task);
    fileRepository.save(attached);
    fileRepository.save(file(team, uploader, "shared.txt", 200));

    List<AppFile> taskFiles = fileRepository.findByTaskId(task.getId());

    assertThat(taskFiles).hasSize(1).first().extracting(AppFile::getFileName).isEqualTo("doc.pdf");
  }

  @Test
  @DisplayName("findByTeamIdAndTaskIsNull: повертає файли спільного простору")
  void findByTeamIdAndTaskIsNull_returnsSharedFiles() {
    AppFile attached = file(team, uploader, "doc.pdf", 100);
    attached.setTask(task);
    fileRepository.save(attached);
    fileRepository.save(file(team, uploader, "shared.txt", 200));

    List<AppFile> shared = fileRepository.findByTeamIdAndTaskIsNull(team.getId());

    assertThat(shared).hasSize(1).first().extracting(AppFile::getFileName).isEqualTo("shared.txt");
  }

  @Test
  @DisplayName("sumFileSizeByTeamId: коректно сумує розміри файлів")
  void sumFileSizeByTeamId_correct() {
    fileRepository.save(file(team, uploader, "a.txt", 1000));
    fileRepository.save(file(team, uploader, "b.txt", 2500));
    fileRepository.save(file(team, uploader, "c.txt", 500));

    assertThat(fileRepository.sumFileSizeByTeamId(team.getId())).isEqualTo(4000L);
  }

  @Test
  @DisplayName("sumFileSizeByTeamId: повертає 0 коли немає файлів (COALESCE)")
  void sumFileSizeByTeamId_emptyTeam_returnsZero() {
    assertThat(fileRepository.sumFileSizeByTeamId(team.getId())).isZero();
  }

  @Test
  @DisplayName("countByTeamId: рахує усі файли команди")
  void countByTeamId_correct() {
    fileRepository.save(file(team, uploader, "f1", 10));
    fileRepository.save(file(team, uploader, "f2", 20));

    assertThat(fileRepository.countByTeamId(team.getId())).isEqualTo(2L);
  }
}

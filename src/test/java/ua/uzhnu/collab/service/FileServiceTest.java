package ua.uzhnu.collab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.uzhnu.collab.dto.Dtos.FileDto;
import ua.uzhnu.collab.entity.*;
import ua.uzhnu.collab.exception.AccessDeniedException;
import ua.uzhnu.collab.exception.EntityNotFoundException;
import ua.uzhnu.collab.repository.AppFileRepository;

/** Юніт-тести {@link FileService}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileService — юніт-тест")
class FileServiceTest {

  @Mock private AppFileRepository fileRepository;
  @Mock private UserService userService;
  @Mock private TeamService teamService;
  @Mock private TaskService taskService;
  @Spy private DtoMapper mapper = new DtoMapper();

  @InjectMocks private FileService fileService;

  private User uploader;
  private Team team;

  @BeforeEach
  void setUp() {
    uploader =
        User.builder().id(1L).username("u").fullName("U").email("u@x").passwordHash("x").build();
    team = Team.builder().id(10L).name("T").createdBy(uploader).build();
  }

  @Test
  @DisplayName("saveFileMetadata без taskId: створює файл у спільному просторі команди")
  void saveFileMetadata_noTask_succeeds() {
    doNothing().when(teamService).requireMember(10L, 1L);
    when(teamService.findTeamById(10L)).thenReturn(team);
    when(userService.findById(1L)).thenReturn(uploader);
    when(fileRepository.save(any(AppFile.class)))
        .thenAnswer(
            inv -> {
              AppFile f = inv.getArgument(0);
              f.setId(99L);
              return f;
            });

    FileDto result =
        fileService.saveFileMetadata(10L, null, 1L, "doc.pdf", 1024L, "application/pdf");

    assertThat(result.id()).isEqualTo(99L);
    assertThat(result.taskId()).isNull();
  }

  @Test
  @DisplayName("saveFileMetadata з taskId: створює файл, прив'язаний до задачі")
  void saveFileMetadata_withTask_succeeds() {
    Task task = Task.builder().id(50L).team(team).title("T").build();

    doNothing().when(teamService).requireMember(10L, 1L);
    when(teamService.findTeamById(10L)).thenReturn(team);
    when(userService.findById(1L)).thenReturn(uploader);
    when(taskService.findTaskById(50L)).thenReturn(task);
    when(fileRepository.save(any(AppFile.class))).thenAnswer(inv -> inv.getArgument(0));

    FileDto result =
        fileService.saveFileMetadata(10L, 50L, 1L, "doc.pdf", 1024L, "application/pdf");

    assertThat(result.taskId()).isEqualTo(50L);
  }

  @Test
  @DisplayName("saveFileMetadata: задача іншої команди — IllegalArgumentException")
  void saveFileMetadata_taskFromOtherTeam_throws() {
    Team otherTeam = Team.builder().id(99L).build();
    Task task = Task.builder().id(50L).team(otherTeam).title("T").build();

    doNothing().when(teamService).requireMember(10L, 1L);
    when(teamService.findTeamById(10L)).thenReturn(team);
    when(userService.findById(1L)).thenReturn(uploader);
    when(taskService.findTaskById(50L)).thenReturn(task);

    assertThatThrownBy(
            () -> fileService.saveFileMetadata(10L, 50L, 1L, "doc.pdf", 1024L, "application/pdf"))
        .isInstanceOf(IllegalArgumentException.class);

    verify(fileRepository, never()).save(any());
  }

  @Test
  @DisplayName("saveFileMetadata: storagePath будується як storage/team_{id}/{name}")
  void saveFileMetadata_storagePathFormat() {
    doNothing().when(teamService).requireMember(10L, 1L);
    when(teamService.findTeamById(10L)).thenReturn(team);
    when(userService.findById(1L)).thenReturn(uploader);

    ArgumentCaptor<AppFile> captor = ArgumentCaptor.forClass(AppFile.class);
    when(fileRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    fileService.saveFileMetadata(10L, null, 1L, "doc.pdf", 1L, "x/y");

    // Path.of з System-залежним сепаратором — перевіряємо лише вміст:
    String path = captor.getValue().getStoragePath();
    assertThat(path).contains("storage", "team_10", "doc.pdf");
  }

  @Test
  @DisplayName(
      "deleteFile: видаляє метадані та повертає storagePath для подальшого видалення з диска")
  void deleteFile_returnsStoragePath() {
    AppFile file =
        AppFile.builder()
            .id(1L)
            .team(team)
            .uploadedBy(uploader)
            .fileName("doc.pdf")
            .storagePath("storage/team_10/doc.pdf")
            .fileSize(100L)
            .mimeType("application/pdf")
            .build();

    when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
    doNothing().when(teamService).requireMember(10L, 1L);

    String path = fileService.deleteFile(1L, 1L);

    assertThat(path).isEqualTo("storage/team_10/doc.pdf");
    verify(fileRepository).delete(file);
  }

  @Test
  @DisplayName("deleteFile: невідомий ID — EntityNotFoundException")
  void deleteFile_unknownId_throws() {
    when(fileRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> fileService.deleteFile(999L, 1L))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  @DisplayName("deleteFile: не учасник — AccessDeniedException")
  void deleteFile_byNonMember_throws() {
    AppFile file =
        AppFile.builder()
            .id(1L)
            .team(team)
            .uploadedBy(uploader)
            .fileName("x")
            .storagePath("p")
            .fileSize(1L)
            .mimeType("m")
            .build();
    when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
    doThrow(new AccessDeniedException("no")).when(teamService).requireMember(10L, 1L);

    assertThatThrownBy(() -> fileService.deleteFile(1L, 1L))
        .isInstanceOf(AccessDeniedException.class);

    verify(fileRepository, never()).delete(any());
  }
}

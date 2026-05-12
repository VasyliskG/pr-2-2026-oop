package ua.uzhnu.collab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.uzhnu.collab.dto.Dtos.TeamCreateDto;
import ua.uzhnu.collab.dto.Dtos.TeamDto;
import ua.uzhnu.collab.dto.Dtos.TeamMemberDto;
import ua.uzhnu.collab.dto.Dtos.TeamStatsDto;
import ua.uzhnu.collab.entity.*;
import ua.uzhnu.collab.enums.TaskStatus;
import ua.uzhnu.collab.enums.TeamRole;
import ua.uzhnu.collab.exception.*;
import ua.uzhnu.collab.repository.*;

/**
 * Юніт-тести {@link TeamService}.
 *
 * <p>Фокус: правила авторизації (OWNER vs ADMIN vs MEMBER), захист від видалення останнього
 * OWNER-а, нормалізація ролей при додаванні.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamService — юніт-тест")
class TeamServiceTest {

  @Mock private TeamRepository teamRepository;
  @Mock private TeamMemberRepository memberRepository;
  @Mock private TaskRepository taskRepository;
  @Mock private AppFileRepository fileRepository;
  @Mock private ChatMessageRepository messageRepository;
  @Mock private UserService userService;
  @Spy private DtoMapper mapper = new DtoMapper();

  @InjectMocks private TeamService teamService;

  private User owner;
  private User member;
  private User admin;
  private Team team;

  @BeforeEach
  void setUp() {
    owner =
        User.builder()
            .id(1L)
            .username("owner")
            .fullName("Owner User")
            .email("o@x")
            .passwordHash("x")
            .build();
    member =
        User.builder()
            .id(2L)
            .username("member")
            .fullName("Member User")
            .email("m@x")
            .passwordHash("x")
            .build();
    admin =
        User.builder()
            .id(3L)
            .username("admin")
            .fullName("Admin User")
            .email("a@x")
            .passwordHash("x")
            .build();
    team = Team.builder().id(10L).name("Team").description("desc").createdBy(owner).build();
  }

  /**
   * Допоміжний метод: налаштовує мок membership для users з заданими ролями.
   *
   * <p>Використовує {@code lenient()}, бо тести можуть користуватися або {@code findBy...}, або
   * {@code existsBy...} — а в Mockito strict-stubs НЕвикористаний стаб → {@code
   * UnnecessaryStubbingException}.
   */
  private void mockMembership(Long userId, TeamRole role) {
    User user =
        switch (userId.intValue()) {
          case 1 -> owner;
          case 2 -> member;
          case 3 -> admin;
          default -> throw new IllegalArgumentException();
        };
    TeamMember tm =
        TeamMember.builder()
            .id(new TeamMemberId(team.getId(), userId))
            .team(team)
            .user(user)
            .role(role)
            .build();
    lenient()
        .when(memberRepository.findByIdTeamIdAndIdUserId(team.getId(), userId))
        .thenReturn(Optional.of(tm));
    lenient()
        .when(memberRepository.existsByIdTeamIdAndIdUserId(team.getId(), userId))
        .thenReturn(true);
  }

  // =====================================================================
  // CREATE
  // =====================================================================

  @Nested
  @DisplayName("create()")
  class CreateTests {

    @Test
    @DisplayName("створення команди: автор автоматично стає OWNER")
    void create_addsCreatorAsOwner() {
      TeamCreateDto dto = new TeamCreateDto("New Team", "Some description");

      when(userService.findById(1L)).thenReturn(owner);
      when(teamRepository.save(any(Team.class)))
          .thenAnswer(
              inv -> {
                Team t = inv.getArgument(0);
                t.setId(100L);
                return t;
              });

      TeamDto result = teamService.create(dto, 1L);

      assertThat(result.id()).isEqualTo(100L);
      assertThat(result.name()).isEqualTo("New Team");
      assertThat(result.memberCount()).isEqualTo(1); // тільки автор

      // Перевіряємо що було збережено саме TeamMember з роллю OWNER
      verify(memberRepository)
          .save(argThat(tm -> tm.getRole() == TeamRole.OWNER && tm.getUser().getId().equals(1L)));
    }
  }

  // =====================================================================
  // ADD MEMBER
  // =====================================================================

  @Nested
  @DisplayName("addMember()")
  class AddMemberTests {

    @Test
    @DisplayName("OWNER може додавати учасників")
    void addMember_byOwner_succeeds() {
      mockMembership(1L, TeamRole.OWNER); // ініціатор — OWNER
      when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
      when(userService.findById(2L)).thenReturn(member);
      when(memberRepository.existsByIdTeamIdAndIdUserId(10L, 2L)).thenReturn(false);
      when(memberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

      TeamMemberDto result = teamService.addMember(10L, 2L, TeamRole.MEMBER, 1L);

      assertThat(result.userId()).isEqualTo(2L);
      assertThat(result.role()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    @DisplayName("MEMBER НЕ може додавати — AccessDeniedException")
    void addMember_byMember_throwsAccessDenied() {
      mockMembership(2L, TeamRole.MEMBER);

      assertThatThrownBy(() -> teamService.addMember(10L, 3L, TeamRole.MEMBER, 2L))
          .isInstanceOf(AccessDeniedException.class);

      verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("додавання вже існуючого учасника: DuplicateEntityException")
    void addMember_alreadyMember_throwsDuplicate() {
      mockMembership(1L, TeamRole.OWNER);
      when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
      when(userService.findById(2L)).thenReturn(member);
      when(memberRepository.existsByIdTeamIdAndIdUserId(10L, 2L)).thenReturn(true);

      assertThatThrownBy(() -> teamService.addMember(10L, 2L, TeamRole.MEMBER, 1L))
          .isInstanceOf(DuplicateEntityException.class);
    }

    @Test
    @DisplayName("спроба надати роль OWNER стороннім — автоматично понижується до ADMIN")
    void addMember_attemptToGrantOwnerRole_downgradedToAdmin() {
      mockMembership(1L, TeamRole.OWNER);
      when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
      when(userService.findById(2L)).thenReturn(member);
      when(memberRepository.existsByIdTeamIdAndIdUserId(10L, 2L)).thenReturn(false);
      when(memberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

      TeamMemberDto result = teamService.addMember(10L, 2L, TeamRole.OWNER, 1L);

      assertThat(result.role()).isEqualTo(TeamRole.ADMIN); // понижено
    }
  }

  // =====================================================================
  // REMOVE MEMBER
  // =====================================================================

  @Nested
  @DisplayName("removeMember()")
  class RemoveMemberTests {

    @Test
    @DisplayName("OWNER видаляє звичайного учасника — успіх")
    void removeMember_regularMember_succeeds() {
      mockMembership(1L, TeamRole.OWNER);
      TeamMember victim =
          TeamMember.builder()
              .id(new TeamMemberId(10L, 2L))
              .team(team)
              .user(member)
              .role(TeamRole.MEMBER)
              .build();
      when(memberRepository.findByIdTeamIdAndIdUserId(10L, 2L)).thenReturn(Optional.of(victim));

      teamService.removeMember(10L, 2L, 1L);

      verify(memberRepository).delete(victim);
    }

    @Test
    @DisplayName("захист: неможливо видалити ОСТАННЬОГО OWNER → CollabException")
    void removeMember_lastOwner_throws() {
      mockMembership(1L, TeamRole.OWNER); // ініціатор
      TeamMember lastOwner =
          TeamMember.builder()
              .id(new TeamMemberId(10L, 1L))
              .team(team)
              .user(owner)
              .role(TeamRole.OWNER)
              .build();
      // Повертаємо ту ж саму інстанцію (бо це ж той самий ID)
      when(memberRepository.findByIdTeamIdAndIdUserId(10L, 1L)).thenReturn(Optional.of(lastOwner));
      when(memberRepository.countByIdTeamIdAndRole(10L, TeamRole.OWNER)).thenReturn(1L);

      assertThatThrownBy(() -> teamService.removeMember(10L, 1L, 1L))
          .isInstanceOf(CollabException.class)
          .hasMessageContaining("останнього власника");

      verify(memberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("видалення OWNER при наявності кількох власників — дозволено")
    void removeMember_owner_whenMultipleOwners_allowed() {
      mockMembership(1L, TeamRole.OWNER);
      TeamMember anotherOwner =
          TeamMember.builder()
              .id(new TeamMemberId(10L, 3L))
              .team(team)
              .user(admin)
              .role(TeamRole.OWNER)
              .build();
      when(memberRepository.findByIdTeamIdAndIdUserId(10L, 3L))
          .thenReturn(Optional.of(anotherOwner));
      when(memberRepository.countByIdTeamIdAndRole(10L, TeamRole.OWNER)).thenReturn(2L);

      teamService.removeMember(10L, 3L, 1L);

      verify(memberRepository).delete(anotherOwner);
    }
  }

  // =====================================================================
  // CHANGE ROLE — тільки OWNER
  // =====================================================================

  @Test
  @DisplayName("changeRole: ADMIN НЕ може змінювати ролі — AccessDeniedException")
  void changeRole_byAdmin_throws() {
    mockMembership(3L, TeamRole.ADMIN);

    assertThatThrownBy(() -> teamService.changeRole(10L, 2L, TeamRole.ADMIN, 3L))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("OWNER");
  }

  // =====================================================================
  // STATISTICS
  // =====================================================================

  @Test
  @DisplayName("getStatistics: total = todo + inProgress + done")
  void getStatistics_aggregatesCorrectly() {
    when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
    when(memberRepository.findByIdTeamId(10L))
        .thenReturn(List.of(new TeamMember(), new TeamMember(), new TeamMember())); // 3 учасники
    when(taskRepository.countByTeamIdAndStatus(10L, TaskStatus.TODO)).thenReturn(5L);
    when(taskRepository.countByTeamIdAndStatus(10L, TaskStatus.IN_PROGRESS)).thenReturn(3L);
    when(taskRepository.countByTeamIdAndStatus(10L, TaskStatus.DONE)).thenReturn(7L);
    when(taskRepository.findOverdueByTeam(10L)).thenReturn(Collections.emptyList());
    when(fileRepository.countByTeamId(10L)).thenReturn(2L);
    when(fileRepository.sumFileSizeByTeamId(10L)).thenReturn(1000L);
    when(messageRepository.countByTeamId(10L)).thenReturn(20L);

    TeamStatsDto stats = teamService.getStatistics(10L);

    assertThat(stats.memberCount()).isEqualTo(3);
    assertThat(stats.todoCount()).isEqualTo(5);
    assertThat(stats.inProgressCount()).isEqualTo(3);
    assertThat(stats.doneCount()).isEqualTo(7);
    assertThat(stats.totalTasks()).isEqualTo(15); // 5 + 3 + 7
    assertThat(stats.overdueCount()).isZero();
    assertThat(stats.fileCount()).isEqualTo(2);
    assertThat(stats.totalFileSizeBytes()).isEqualTo(1000L);
    assertThat(stats.messageCount()).isEqualTo(20L);
  }
}

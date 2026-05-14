package ua.uzhnu.collab.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.uzhnu.collab.dto.Dtos.*;
import ua.uzhnu.collab.entity.*;
import ua.uzhnu.collab.enums.TaskStatus;
import ua.uzhnu.collab.enums.TeamRole;
import ua.uzhnu.collab.exception.*;
import ua.uzhnu.collab.repository.*;

/**
 * Сервіс бізнес-логіки для сутностей {@link Team} та {@link TeamMember}.
 *
 * <p>Забезпечує створення команд, управління учасниками (додавання, видалення, зміна ролі),
 * отримання статистики.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class TeamService {

  private final TeamRepository teamRepository;
  private final TeamMemberRepository memberRepository;
  private final TaskRepository taskRepository;
  private final AppFileRepository fileRepository;
  private final ChatMessageRepository messageRepository;
  private final UserService userService;
  private final DtoMapper mapper;

  // =====================================================================
  // СТВОРЕННЯ КОМАНДИ
  // =====================================================================

  /** Створює команду та додає автора як OWNER. */
  @Transactional
  public TeamDto create(TeamCreateDto dto, Long creatorUserId) {
    User creator = userService.findById(creatorUserId);

    Team team =
        Team.builder()
            .name(dto.name().trim())
            .description(dto.description())
            .createdBy(creator)
            .build();

    team = teamRepository.save(team);

    // Автоматично додаємо автора як OWNER
    TeamMember ownership =
        TeamMember.builder()
            .id(new TeamMemberId(team.getId(), creator.getId()))
            .team(team)
            .user(creator)
            .role(TeamRole.OWNER)
            .build();
    memberRepository.save(ownership);

    log.info(
        "Створено команду '{}' (id={}), власник: {}",
        team.getName(),
        team.getId(),
        creator.getUsername());
    return mapper.toTeamDto(team, 1, 0);
  }

  // =====================================================================
  // УПРАВЛІННЯ УЧАСНИКАМИ
  // =====================================================================

  /**
   * Додає користувача до команди.
   *
   * @throws DuplicateEntityException якщо вже є учасником
   * @throws AccessDeniedException якщо ініціатор не має права
   */
  @Transactional
  public TeamMemberDto addMember(Long teamId, Long userId, TeamRole role, Long initiatorId) {
    requireAdminOrOwner(teamId, initiatorId);
    Team team = findTeamById(teamId);
    User user = userService.findById(userId);

    if (memberRepository.existsByIdTeamIdAndIdUserId(teamId, userId)) {
      throw new DuplicateEntityException(
          "Користувач " + user.getFullName() + " вже є учасником команди");
    }

    // Заборона надання ролі OWNER стороннім
    TeamRole effectiveRole = (role == TeamRole.OWNER) ? TeamRole.ADMIN : role;

    TeamMember member =
        TeamMember.builder()
            .id(new TeamMemberId(teamId, userId))
            .team(team)
            .user(user)
            .role(effectiveRole)
            .build();

    member = memberRepository.save(member);
    log.info(
        "Додано учасника {} до команди {} з роллю {}",
        user.getUsername(),
        team.getName(),
        effectiveRole);
    return mapper.toTeamMemberDto(member);
  }

  /**
   * Видаляє учасника з команди.
   *
   * @throws CollabException якщо це останній OWNER
   */
  @Transactional
  public void removeMember(Long teamId, Long userId, Long initiatorId) {
    requireAdminOrOwner(teamId, initiatorId);

    TeamMember member =
        memberRepository
            .findByIdTeamIdAndIdUserId(teamId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Учасник команди", userId));

    // Захист останнього власника
    if (member.getRole() == TeamRole.OWNER) {
      long ownerCount = memberRepository.countByIdTeamIdAndRole(teamId, TeamRole.OWNER);
      if (ownerCount <= 1) {
        throw new CollabException("Неможливо видалити останнього власника команди");
      }
    }

    memberRepository.delete(member);
    log.info("Видалено учасника {} з команди id={}", member.getUser().getUsername(), teamId);
  }

  /**
   * Виходить з команди (self-service).
   *
   * @throws CollabException якщо це останній OWNER
   */
  @Transactional
  public void leaveTeam(Long teamId, Long userId) {
    TeamMember member =
        memberRepository
            .findByIdTeamIdAndIdUserId(teamId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Учасник команди", userId));

    if (member.getRole() == TeamRole.OWNER) {
      long ownerCount = memberRepository.countByIdTeamIdAndRole(teamId, TeamRole.OWNER);
      if (ownerCount <= 1) {
        throw new CollabException("Неможливо покинути команду — ви єдиний власник. Передайте роль іншому або видаліть команду.");
      }
    }

    memberRepository.delete(member);
    log.info("Користувач {} покинув команду id={}", member.getUser().getUsername(), teamId);
  }

  /**
   * Видаляє команду разом з усіма даними.
   *
   * @throws AccessDeniedException якщо ініціатор не є OWNER
   */
  @Transactional
  public void deleteTeam(Long teamId, Long initiatorId) {
    requireOwner(teamId, initiatorId);
    Team team = findTeamById(teamId);
    teamRepository.delete(team);
    log.info("Команду '{}' (id={}) видалено власником id={}", team.getName(), teamId, initiatorId);
  }

  /** Змінює роль учасника. */
  @Transactional
  public TeamMemberDto changeRole(Long teamId, Long userId, TeamRole newRole, Long initiatorId) {
    requireOwner(teamId, initiatorId);

    TeamMember member =
        memberRepository
            .findByIdTeamIdAndIdUserId(teamId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Учасник команди", userId));

    member.setRole(newRole);
    member = memberRepository.save(member);
    log.info(
        "Змінено роль {} на {} у команді id={}", member.getUser().getUsername(), newRole, teamId);
    return mapper.toTeamMemberDto(member);
  }

  // =====================================================================
  // ОТРИМАННЯ
  // =====================================================================

  public TeamDto getById(Long teamId) {
    Team team = findTeamById(teamId);
    int memberCount = memberRepository.findByIdTeamId(teamId).size();
    int taskCount = taskRepository.findByTeamIdOrderByCreatedAtDesc(teamId).size();
    return mapper.toTeamDto(team, memberCount, taskCount);
  }

  /** Команди користувача. */
  public List<TeamDto> getTeamsByUser(Long userId) {
    return teamRepository.findByUserId(userId).stream()
        .map(
            team -> {
              int mc = memberRepository.findByIdTeamId(team.getId()).size();
              int tc = taskRepository.findByTeamIdOrderByCreatedAtDesc(team.getId()).size();
              return mapper.toTeamDto(team, mc, tc);
            })
        .toList();
  }

  /** Учасники команди. */
  public List<TeamMemberDto> getMembers(Long teamId) {
    return memberRepository.findByIdTeamId(teamId).stream().map(mapper::toTeamMemberDto).toList();
  }

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
                  userId, member.getUser().getFullName(), 0, todo, inProg, done, overdue);
            })
        .toList();
  }

  /** Статистика команди для дашборду. */
  public TeamStatsDto getStatistics(Long teamId) {
    Team team = findTeamById(teamId);
    int members = memberRepository.findByIdTeamId(teamId).size();

    int todo = (int) taskRepository.countByTeamIdAndStatus(teamId, TaskStatus.TODO);
    int inProg = (int) taskRepository.countByTeamIdAndStatus(teamId, TaskStatus.IN_PROGRESS);
    int done = (int) taskRepository.countByTeamIdAndStatus(teamId, TaskStatus.DONE);
    int total = todo + inProg + done;

    int overdue = taskRepository.findOverdueByTeam(teamId).size();
    int files = (int) fileRepository.countByTeamId(teamId);
    long fSize = fileRepository.sumFileSizeByTeamId(teamId);
    long msgs = messageRepository.countByTeamId(teamId);

    return new TeamStatsDto(
        team.getId(),
        team.getName(),
        members,
        total,
        todo,
        inProg,
        done,
        overdue,
        files,
        fSize,
        msgs);
  }

  // =====================================================================
  // ВНУТРІШНІ МЕТОДИ
  // =====================================================================

  Team findTeamById(Long id) {
    return teamRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Команда", id));
  }

  /** Перевіряє, що ініціатор є учасником команди з роллю ADMIN або OWNER. */
  void requireAdminOrOwner(Long teamId, Long userId) {
    TeamMember member =
        memberRepository
            .findByIdTeamIdAndIdUserId(teamId, userId)
            .orElseThrow(() -> new AccessDeniedException("Користувач не є учасником команди"));

    if (member.getRole() == TeamRole.MEMBER) {
      throw new AccessDeniedException(
          "Недостатня роль для виконання операції (потрібно ADMIN або OWNER)");
    }
  }

  void requireOwner(Long teamId, Long userId) {
    TeamMember member =
        memberRepository
            .findByIdTeamIdAndIdUserId(teamId, userId)
            .orElseThrow(() -> new AccessDeniedException("Користувач не є учасником команди"));

    if (member.getRole() != TeamRole.OWNER) {
      throw new AccessDeniedException("Для цієї операції потрібна роль OWNER");
    }
  }

  void requireMember(Long teamId, Long userId) {
    if (!memberRepository.existsByIdTeamIdAndIdUserId(teamId, userId)) {
      throw new AccessDeniedException("Користувач не є учасником команди");
    }
  }
}

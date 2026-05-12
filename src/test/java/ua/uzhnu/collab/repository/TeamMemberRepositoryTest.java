package ua.uzhnu.collab.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.uzhnu.collab.TestData.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ua.uzhnu.collab.AbstractRepositoryIntegrationTest;
import ua.uzhnu.collab.entity.Team;
import ua.uzhnu.collab.entity.TeamMember;
import ua.uzhnu.collab.entity.User;
import ua.uzhnu.collab.enums.TeamRole;

@DisplayName("TeamMemberRepository — інтеграційний тест")
class TeamMemberRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private TeamMemberRepository memberRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;

  private User owner;
  private User member;
  private Team team;

  @BeforeEach
  void setUp() {
    owner = userRepository.save(user("owner"));
    member = userRepository.save(user("member"));
    team = teamRepository.save(team("Squad", owner));
  }

  @Test
  @DisplayName("save: створює членство з композитним ключем")
  void save_persistsWithCompositeId() {
    TeamMember tm = memberRepository.save(membership(team, owner, TeamRole.OWNER));

    assertThat(tm.getId()).isNotNull();
    assertThat(tm.getId().getTeamId()).isEqualTo(team.getId());
    assertThat(tm.getId().getUserId()).isEqualTo(owner.getId());
    assertThat(tm.getJoinedAt()).isNotNull();
  }

  @Test
  @DisplayName("findByIdTeamId: повертає всіх учасників команди")
  void findByIdTeamId_returnsAllMembersOfTeam() {
    memberRepository.save(membership(team, owner, TeamRole.OWNER));
    memberRepository.save(membership(team, member, TeamRole.MEMBER));

    List<TeamMember> members = memberRepository.findByIdTeamId(team.getId());

    assertThat(members).hasSize(2);
  }

  @Test
  @DisplayName("findByIdUserId: повертає всі команди користувача")
  void findByIdUserId_returnsAllTeamsOfUser() {
    Team team2 = teamRepository.save(team("Second", owner));

    memberRepository.save(membership(team, owner, TeamRole.OWNER));
    memberRepository.save(membership(team2, owner, TeamRole.MEMBER));

    List<TeamMember> teams = memberRepository.findByIdUserId(owner.getId());

    assertThat(teams).hasSize(2);
  }

  @Test
  @DisplayName("findByIdTeamIdAndIdUserId: повертає конкретне членство")
  void findByIdTeamIdAndIdUserId_returnsSpecificMembership() {
    memberRepository.save(membership(team, owner, TeamRole.OWNER));

    Optional<TeamMember> found =
        memberRepository.findByIdTeamIdAndIdUserId(team.getId(), owner.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getRole()).isEqualTo(TeamRole.OWNER);
  }

  @Test
  @DisplayName("findByIdTeamIdAndIdUserId: повертає empty якщо не учасник")
  void findByIdTeamIdAndIdUserId_notMember_returnsEmpty() {
    assertThat(memberRepository.findByIdTeamIdAndIdUserId(team.getId(), member.getId())).isEmpty();
  }

  @Test
  @DisplayName("existsByIdTeamIdAndIdUserId: true/false коректно")
  void existsByIdTeamIdAndIdUserId_works() {
    memberRepository.save(membership(team, owner, TeamRole.OWNER));

    assertThat(memberRepository.existsByIdTeamIdAndIdUserId(team.getId(), owner.getId())).isTrue();
    assertThat(memberRepository.existsByIdTeamIdAndIdUserId(team.getId(), member.getId()))
        .isFalse();
  }

  @Test
  @DisplayName("countByIdTeamIdAndRole: рахує учасників із заданою роллю")
  void countByIdTeamIdAndRole_correct() {
    User adm = userRepository.save(user("admin"));

    memberRepository.save(membership(team, owner, TeamRole.OWNER));
    memberRepository.save(membership(team, member, TeamRole.MEMBER));
    memberRepository.save(membership(team, adm, TeamRole.ADMIN));

    assertThat(memberRepository.countByIdTeamIdAndRole(team.getId(), TeamRole.OWNER)).isEqualTo(1L);
    assertThat(memberRepository.countByIdTeamIdAndRole(team.getId(), TeamRole.ADMIN)).isEqualTo(1L);
    assertThat(memberRepository.countByIdTeamIdAndRole(team.getId(), TeamRole.MEMBER))
        .isEqualTo(1L);
  }
}

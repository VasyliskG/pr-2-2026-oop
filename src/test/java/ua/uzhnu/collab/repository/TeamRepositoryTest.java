package ua.uzhnu.collab.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.uzhnu.collab.TestData.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ua.uzhnu.collab.AbstractRepositoryIntegrationTest;
import ua.uzhnu.collab.entity.Team;
import ua.uzhnu.collab.entity.User;
import ua.uzhnu.collab.enums.TeamRole;

@DisplayName("TeamRepository — інтеграційний тест")
class TeamRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamMemberRepository memberRepository;

  @Test
  @DisplayName("save: зберігає команду з FK на creator")
  void save_persistsTeamWithCreatorFK() {
    User creator = userRepository.save(user("creator"));

    Team saved = teamRepository.save(team("Backend Team", creator));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedBy().getId()).isEqualTo(creator.getId());
    assertThat(saved.getName()).isEqualTo("Backend Team");
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("findByUserId: повертає лише команди, де користувач є учасником")
  void findByUserId_filtersToMembership() {
    User alice = userRepository.save(user("alice"));
    User bob = userRepository.save(user("bob"));

    Team team1 = teamRepository.save(team("Alpha", alice));
    Team team2 = teamRepository.save(team("Beta", alice));
    Team team3 = teamRepository.save(team("Gamma", bob));

    memberRepository.save(membership(team1, alice, TeamRole.OWNER));
    memberRepository.save(membership(team2, alice, TeamRole.MEMBER));
    memberRepository.save(membership(team3, bob, TeamRole.OWNER));

    List<Team> alicesTeams = teamRepository.findByUserId(alice.getId());

    assertThat(alicesTeams)
        .extracting(Team::getName)
        .containsExactly("Alpha", "Beta")
        .doesNotContain("Gamma");
  }

  @Test
  @DisplayName("findByUserId: користувач без команд → порожній список")
  void findByUserId_noMemberships_returnsEmpty() {
    User loner = userRepository.save(user("loner"));

    assertThat(teamRepository.findByUserId(loner.getId())).isEmpty();
  }

  @Test
  @DisplayName("findByNameContainingIgnoreCase: case-insensitive пошук за фрагментом")
  void findByNameContainingIgnoreCase_works() {
    User u = userRepository.save(user("creator"));
    teamRepository.save(team("Frontend Squad", u));
    teamRepository.save(team("Backend Squad", u));
    teamRepository.save(team("QA Team", u));

    List<Team> squads = teamRepository.findByNameContainingIgnoreCase("SQUAD");

    assertThat(squads)
        .extracting(Team::getName)
        .containsExactlyInAnyOrder("Frontend Squad", "Backend Squad");
  }
}

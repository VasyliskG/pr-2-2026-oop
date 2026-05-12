package ua.uzhnu.collab.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.uzhnu.collab.TestData.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import ua.uzhnu.collab.AbstractRepositoryIntegrationTest;
import ua.uzhnu.collab.entity.User;
import ua.uzhnu.collab.enums.TeamRole;

@DisplayName("UserRepository — інтеграційний тест")
class UserRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private TeamMemberRepository memberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  // =====================================================================
  // CRUD
  // =====================================================================

  @Test
  @DisplayName("save: створює користувача та присвоює id")
  void save_assignsIdAndPersistsAllFields() {
    User saved = userRepository.save(user("alice"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getUsername()).isEqualTo("alice");
    assertThat(saved.getEmail()).isEqualTo("alice@uzhnu.test");
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("findById: повертає збереженого користувача")
  void findById_returnsSavedEntity() {
    User saved = userRepository.save(user("bob"));

    Optional<User> found = userRepository.findById(saved.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getUsername()).isEqualTo("bob");
  }

  @Test
  @DisplayName("delete: видаляє користувача з БД")
  void delete_removesEntity() {
    User saved = userRepository.save(user("carol"));
    userRepository.delete(saved);

    assertThat(userRepository.findById(saved.getId())).isEmpty();
  }

  // =====================================================================
  // UNIQUE CONSTRAINTS
  // =====================================================================

  @Test
  @DisplayName("save: дубль username викидає виключення")
  void save_duplicateUsername_throws() {
    userRepository.save(user("dave"));
    User clone = user("dave");
    clone.setEmail("other@uzhnu.test");

    assertThatThrownBy(() -> userRepository.save(clone))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("save: дубль email викидає виключення")
  void save_duplicateEmail_throws() {
    User a = userRepository.save(user("eve"));
    User b = user("eveDuplicate");
    b.setEmail(a.getEmail());

    assertThatThrownBy(() -> userRepository.save(b))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // =====================================================================
  // DERIVED QUERIES
  // =====================================================================

  @Test
  @DisplayName("findByUsername: знаходить за логіном")
  void findByUsername_returnsUser() {
    userRepository.save(user("frank"));

    Optional<User> found = userRepository.findByUsername("frank");

    assertThat(found).isPresent();
    assertThat(found.get().getEmail()).isEqualTo("frank@uzhnu.test");
  }

  @Test
  @DisplayName("findByUsername: повертає Optional.empty якщо логіну немає")
  void findByUsername_missing_returnsEmpty() {
    assertThat(userRepository.findByUsername("ghost")).isEmpty();
  }

  @Test
  @DisplayName("findByEmail: знаходить за поштою")
  void findByEmail_returnsUser() {
    userRepository.save(user("grace"));

    Optional<User> found = userRepository.findByEmail("grace@uzhnu.test");

    assertThat(found).isPresent();
    assertThat(found.get().getUsername()).isEqualTo("grace");
  }

  @Test
  @DisplayName("existsByUsername: повертає true/false коректно")
  void existsByUsername_works() {
    userRepository.save(user("henry"));

    assertThat(userRepository.existsByUsername("henry")).isTrue();
    assertThat(userRepository.existsByUsername("nope")).isFalse();
  }

  @Test
  @DisplayName("existsByEmail: повертає true/false коректно")
  void existsByEmail_works() {
    userRepository.save(user("ivy"));

    assertThat(userRepository.existsByEmail("ivy@uzhnu.test")).isTrue();
    assertThat(userRepository.existsByEmail("nobody@uzhnu.test")).isFalse();
  }

  // =====================================================================
  // CUSTOM QUERIES
  // =====================================================================

  @Test
  @DisplayName("findByTeamId: повертає всіх учасників команди, відсортованих за fullName")
  void findByTeamId_returnsTeamMembers_sortedByFullName() {
    User owner = userRepository.save(user("aaaa"));
    User other = userRepository.save(user("bbbb"));
    User outsider = userRepository.save(user("zzzz"));

    var savedTeam = teamRepository.save(team("Team1", owner));
    memberRepository.save(membership(savedTeam, owner, TeamRole.OWNER));
    memberRepository.save(membership(savedTeam, other, TeamRole.MEMBER));

    List<User> members = userRepository.findByTeamId(savedTeam.getId());

    assertThat(members)
        .extracting(User::getUsername)
        .containsExactly("aaaa", "bbbb")
        .doesNotContain(outsider.getUsername());
  }

  @Test
  @DisplayName("searchByNameOrUsername: знаходить за частковим співпадінням з case-insensitive")
  void searchByNameOrUsername_findsByPartialMatch() {
    userRepository.save(user("johnsmith"));
    userRepository.save(user("alicewonder"));
    userRepository.save(user("bobsmith"));

    List<User> bySmith = userRepository.searchByNameOrUsername("SMITH");
    assertThat(bySmith)
        .extracting(User::getUsername)
        .containsExactlyInAnyOrder("johnsmith", "bobsmith");

    List<User> byWonder = userRepository.searchByNameOrUsername("wonder");
    assertThat(byWonder).hasSize(1);
    assertThat(byWonder.get(0).getUsername()).isEqualTo("alicewonder");
  }

  @Test
  @DisplayName("searchByNameOrUsername: порожній результат коли немає збігів")
  void searchByNameOrUsername_noMatches_returnsEmpty() {
    userRepository.save(user("alice"));

    assertThat(userRepository.searchByNameOrUsername("xyz_nonexistent")).isEmpty();
  }

  // =====================================================================
  // TIMESTAMPS
  // =====================================================================

  @Test
  @DisplayName("save: оновлення updated_at при модифікації")
  void save_updatesTimestamp() throws InterruptedException {
    User saved = userRepository.save(user("kim"));
    var originalUpdated = saved.getUpdatedAt();
    Long id = saved.getId();

    Thread.sleep(20);

    saved.setFullName("Kim Updated");
    userRepository.save(saved);

    User reloaded = userRepository.findById(id).orElseThrow();

    assertThat(reloaded.getFullName()).isEqualTo("Kim Updated");
    assertThat(reloaded.getUpdatedAt()).isAfter(originalUpdated);
  }
}

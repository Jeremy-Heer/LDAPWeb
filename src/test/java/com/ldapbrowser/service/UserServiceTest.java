package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Unit tests for {@link UserService}.
 * Verifies user store CRUD operations, BCrypt hashing,
 * first-run initialization, and file permission enforcement.
 */
class UserServiceTest {

  @TempDir
  Path tempDir;

  private BCryptPasswordEncoder encoder;
  private UserService service;

  @BeforeEach
  void setUp() {
    encoder = new BCryptPasswordEncoder();
    service = new UserService(tempDir.toString(), encoder);
  }

  // ---------------------------------------------------------------
  // First-run initialisation
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("First-run initialisation")
  class Initialisation {

    @Test
    @DisplayName("creates default admin user on first run")
    void createsDefaultAdmin() {
      List<UserService.UserRecord> users = service.loadUsers();
      assertThat(users).hasSize(1);
      assertThat(users.get(0).username()).isEqualTo("admin");
      assertThat(users.get(0).roles()).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("default admin password is BCrypt-hashed")
    void defaultPasswordIsHashed() {
      List<UserService.UserRecord> users = service.loadUsers();
      String hash = users.get(0).passwordHash();
      assertThat(hash).startsWith("$2a$");
    }

    @Test
    @DisplayName("users.json has restrictive permissions on POSIX systems")
    void filePermissions() {
      Path usersFile = tempDir.resolve("users.json");
      assertThat(usersFile).exists();
      try {
        Set<PosixFilePermission> perms =
            Files.getPosixFilePermissions(usersFile);
        assertThat(perms).containsExactlyInAnyOrder(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);
      } catch (UnsupportedOperationException e) {
        // Non-POSIX system — skip assertion
      } catch (IOException e) {
        // Should not happen with temp dir
        throw new RuntimeException(e);
      }
    }

    @Test
    @DisplayName("does not recreate file if it already exists")
    void doesNotOverwrite() throws IOException {
      // First service creates the file
      List<UserService.UserRecord> original = service.loadUsers();
      String originalHash = original.get(0).passwordHash();

      // Second service should load existing users, not overwrite
      UserService second = new UserService(tempDir.toString(), encoder);
      List<UserService.UserRecord> reloaded = second.loadUsers();
      assertThat(reloaded.get(0).passwordHash()).isEqualTo(originalHash);
    }
  }

  // ---------------------------------------------------------------
  // Spring Security integration
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("UserDetailsService")
  class UserDetailsServiceTests {

    @Test
    @DisplayName("loadUserByUsername returns valid UserDetails")
    void loadExistingUser() {
      UserDetails details = service.loadUserByUsername("admin");
      assertThat(details.getUsername()).isEqualTo("admin");
      assertThat(details.getAuthorities())
          .extracting("authority")
          .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("loadUserByUsername throws for unknown user")
    void loadUnknownUser() {
      assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
          .isInstanceOf(UsernameNotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // CRUD operations
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("CRUD operations")
  class CrudOperations {

    @Test
    @DisplayName("addUser creates a new user with hashed password")
    void addUser() throws IOException {
      service.addUser("viewer", "password123",
          Set.of("VIEWER"));

      List<UserService.UserRecord> users = service.loadUsers();
      assertThat(users).hasSize(2);

      UserService.UserRecord viewer = users.stream()
          .filter(u -> u.username().equals("viewer"))
          .findFirst()
          .orElseThrow();
      assertThat(viewer.roles()).containsExactly("VIEWER");
      assertThat(encoder.matches("password123", viewer.passwordHash()))
          .isTrue();
    }

    @Test
    @DisplayName("addUser rejects duplicate username")
    void addDuplicate() {
      assertThatThrownBy(() ->
          service.addUser("admin", "other", Set.of("ADMIN")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("admin");
    }

    @Test
    @DisplayName("removeUser deletes the account")
    void removeUser() throws IOException {
      service.addUser("temp", "pass", Set.of("VIEWER"));
      assertThat(service.loadUsers()).hasSize(2);

      service.removeUser("temp");
      assertThat(service.loadUsers()).hasSize(1);
    }

    @Test
    @DisplayName("changePassword updates the hash")
    void changePassword() throws IOException {
      service.changePassword("admin", "newPassword");

      UserService.UserRecord admin = service.loadUsers().get(0);
      assertThat(encoder.matches("newPassword", admin.passwordHash()))
          .isTrue();
    }

    @Test
    @DisplayName("changePassword throws for unknown user")
    void changePasswordUnknown() {
      assertThatThrownBy(() ->
          service.changePassword("ghost", "pass"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}

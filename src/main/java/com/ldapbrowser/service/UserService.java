package com.ldapbrowser.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * User management service for local authentication mode.
 *
 * <p>Stores user accounts in {@code users.json} inside the
 * application settings directory. Passwords are hashed with BCrypt.
 *
 * <p>On first run, if no users file exists, a default admin account
 * is created with a random password that is printed to the console.
 */
@Service
@ConditionalOnProperty(name = "ldapbrowser.auth.mode", havingValue = "local")
public class UserService implements UserDetailsService {

  private static final Logger logger = LoggerFactory.getLogger(UserService.class);
  private static final String USERS_FILE = "users.json";
  private static final int GENERATED_PASSWORD_BYTES = 24;

  private final Path usersPath;
  private final ObjectMapper objectMapper;
  private final PasswordEncoder passwordEncoder;

  /**
   * Creates the user service and initialises the users file.
   *
   * @param settingsDir application settings directory
   * @param passwordEncoder BCrypt password encoder
   */
  public UserService(
      @Value("${ldapbrowser.settings.dir}") String settingsDir,
      PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
    this.usersPath = Path.of(settingsDir).resolve(USERS_FILE);
    this.objectMapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();
    ensureUsersFileExists();
  }

  @Override
  public UserDetails loadUserByUsername(String username)
      throws UsernameNotFoundException {
    List<UserRecord> users = loadUsers();
    UserRecord record = users.stream()
        .filter(u -> u.username().equals(username))
        .findFirst()
        .orElseThrow(() -> new UsernameNotFoundException(
            "User not found: " + username));

    List<SimpleGrantedAuthority> authorities = record.roles().stream()
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .toList();

    return new User(record.username(), record.passwordHash(), authorities);
  }

  /**
   * Returns all stored user records.
   *
   * @return list of user records
   */
  public List<UserRecord> loadUsers() {
    try {
      if (!Files.exists(usersPath)) {
        return new ArrayList<>();
      }
      UserRecord[] records =
          objectMapper.readValue(usersPath.toFile(), UserRecord[].class);
      return new ArrayList<>(List.of(records));
    } catch (JacksonException e) {
      logger.error("Failed to load users file", e);
      return new ArrayList<>();
    }
  }

  /**
   * Saves the user list, applying restrictive file permissions.
   *
   * @param users complete list of user records to persist
   * @throws IOException if the file cannot be written
   */
  public void saveUsers(List<UserRecord> users) throws IOException {
    objectMapper.writeValue(usersPath.toFile(), users);
    setRestrictivePermissions(usersPath);
    logger.info("Saved {} user(s) to {}", users.size(), usersPath);
  }

  /**
   * Adds a new user with a hashed password.
   *
   * @param username login name
   * @param clearPassword cleartext password
   * @param roles set of role names (e.g. ADMIN, VIEWER)
   * @throws IOException if save fails
   */
  public void addUser(String username, String clearPassword,
      Set<String> roles) throws IOException {
    List<UserRecord> users = loadUsers();
    if (users.stream().anyMatch(u -> u.username().equals(username))) {
      throw new IllegalArgumentException(
          "User already exists: " + username);
    }
    String hash = passwordEncoder.encode(clearPassword);
    users.add(new UserRecord(username, hash, new ArrayList<>(roles)));
    saveUsers(users);
  }

  /**
   * Removes a user by username.
   *
   * @param username user to remove
   * @throws IOException if save fails
   */
  public void removeUser(String username) throws IOException {
    List<UserRecord> users = loadUsers();
    users.removeIf(u -> u.username().equals(username));
    saveUsers(users);
  }

  /**
   * Updates a user's password.
   *
   * @param username user whose password to change
   * @param newPassword new cleartext password
   * @throws IOException if save fails
   */
  public void changePassword(String username, String newPassword)
      throws IOException {
    List<UserRecord> users = loadUsers();
    Optional<UserRecord> existing = users.stream()
        .filter(u -> u.username().equals(username))
        .findFirst();
    if (existing.isEmpty()) {
      throw new IllegalArgumentException("User not found: " + username);
    }
    int idx = users.indexOf(existing.get());
    String hash = passwordEncoder.encode(newPassword);
    users.set(idx,
        new UserRecord(username, hash, existing.get().roles()));
    saveUsers(users);
  }

  // ------------------------------------------------------------------
  // Initialisation
  // ------------------------------------------------------------------

  /**
   * Creates the users file with a default admin account when it does not
   * exist. The generated password is logged to the console exactly once.
   */
  private void ensureUsersFileExists() {
    if (Files.exists(usersPath)) {
      logger.info("Users file found: {}", usersPath);
      return;
    }
    try {
      Path parent = usersPath.getParent();
      if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent);
      }
      String generatedPassword = generateRandomPassword();
      String hash = passwordEncoder.encode(generatedPassword);
      List<UserRecord> defaultUsers = List.of(
          new UserRecord("admin", hash, List.of("ADMIN")));
      saveUsers(new ArrayList<>(defaultUsers));

      logger.info("============================================");
      logger.info("  Initial admin account created");
      logger.info("  Username : admin");
      logger.info("  Password : {}", generatedPassword);
      logger.info("  Change this password after first login!");
      logger.info("============================================");
    } catch (IOException e) {
      logger.error("Failed to create default users file", e);
    }
  }

  private String generateRandomPassword() {
    byte[] bytes = new byte[GENERATED_PASSWORD_BYTES];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Sets owner-only read/write permissions (Unix-like systems).
   *
   * @param path file path
   */
  private void setRestrictivePermissions(Path path) {
    try {
      Set<PosixFilePermission> perms = new HashSet<>();
      perms.add(PosixFilePermission.OWNER_READ);
      perms.add(PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(path, perms);
      logger.debug("Set restrictive permissions on: {}", path);
    } catch (UnsupportedOperationException e) {
      logger.debug("POSIX permissions not supported on this system");
    } catch (IOException e) {
      logger.warn("Failed to set restrictive permissions on: {}", path, e);
    }
  }

  // ------------------------------------------------------------------
  // User record (serialized to/from JSON)
  // ------------------------------------------------------------------

  /**
   * Represents a persisted user account.
   *
   * @param username login name
   * @param passwordHash BCrypt hash
   * @param roles list of role names (e.g. ADMIN, VIEWER)
   */
  public record UserRecord(
      String username,
      String passwordHash,
      List<String> roles) {
  }
}

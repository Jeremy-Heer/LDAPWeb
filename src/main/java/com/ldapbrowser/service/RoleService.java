package com.ldapbrowser.service;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.model.Role;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Service for managing application roles.
 *
 * <p>Roles are persisted in {@code roles.json} inside the application
 * settings directory. Each role defines which servers, users, and
 * application views are accessible to its members.
 *
 * <p>On first run (when {@code roles.json} does not exist), a default
 * "Admin" role is created that grants access to all views and all
 * currently configured servers.
 */
@Service
public class RoleService {

  private static final Logger logger =
      LoggerFactory.getLogger(RoleService.class);
  private static final String ROLES_FILE = "roles.json";

  private final ObjectMapper objectMapper;
  private final Path rolesPath;
  private final ConfigurationService configurationService;
  private final UserService userService;
  private final String authMode;

  /**
   * Creates the role service.
   *
   * @param settingsDir application settings directory
   * @param configurationService server configuration service
   * @param userService optional user service (local auth only)
   * @param authMode active authentication mode
   */
  public RoleService(
      @Value("${ldapbrowser.settings.dir}") String settingsDir,
      ConfigurationService configurationService,
      @Lazy Optional<UserService> userService,
      @Value("${ldapbrowser.auth.mode:none}") String authMode) {
    this.configurationService = configurationService;
    this.userService = userService.orElse(null);
    this.authMode = authMode;
    this.objectMapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();
    this.rolesPath = Path.of(settingsDir).resolve(ROLES_FILE);
    ensureDirectoryExists();
  }

  /**
   * Initialises default roles on first run after upgrade.
   */
  @PostConstruct
  void initDefaultRoles() {
    if ("none".equals(authMode)) {
      return;
    }
    if (Files.exists(rolesPath)) {
      logger.info("Roles file found: {}", rolesPath);
      ensureAdminUsersHaveRoles();
      return;
    }
    logger.info("Roles file not found – creating default roles");
    try {
      createMigrationRoles();
    } catch (IOException e) {
      logger.error("Failed to create default roles", e);
    }
  }

  /**
   * Ensures admin users from the user store are members of at
   * least one role. Handles the case where {@code roles.json} was
   * created in {@code none} mode without user membership.
   */
  private void ensureAdminUsersHaveRoles() {
    if (userService == null) {
      return;
    }
    List<UserService.UserRecord> users = userService.loadUsers();
    List<String> adminUsers = users.stream()
        .filter(u -> u.roles().contains("ADMIN"))
        .map(UserService.UserRecord::username)
        .toList();
    if (adminUsers.isEmpty()) {
      return;
    }

    List<Role> roles = loadRoles();
    List<String> orphanedAdmins = adminUsers.stream()
        .filter(username -> roles.stream()
            .noneMatch(r -> r.getUserMembers().contains(username)))
        .toList();
    if (orphanedAdmins.isEmpty()) {
      return;
    }

    logger.info("Admin users not in any role: {} – adding to "
        + "Admin role", orphanedAdmins);

    Optional<Role> existing = roles.stream()
        .filter(r -> "Admin".equals(r.getName()))
        .findFirst();

    if (existing.isPresent()) {
      List<String> members =
          new ArrayList<>(existing.get().getUserMembers());
      members.addAll(orphanedAdmins);
      existing.get().setUserMembers(members);
    } else {
      Role adminRole = new Role("Admin");
      adminRole.setAllowedViews(
          new ArrayList<>(Role.ALL_VIEWS));
      List<String> serverNames = configurationService
          .loadConfigurations().stream()
          .map(LdapServerConfig::getName)
          .toList();
      adminRole.setServerMembers(
          new ArrayList<>(serverNames));
      adminRole.setUserMembers(
          new ArrayList<>(orphanedAdmins));
      roles.add(adminRole);
    }

    try {
      saveRoles(roles);
    } catch (IOException e) {
      logger.error("Failed to update roles with admin users", e);
    }
  }

  // ------------------------------------------------------------------
  // CRUD operations
  // ------------------------------------------------------------------

  /**
   * Loads all roles from file.
   *
   * @return list of roles
   */
  public List<Role> loadRoles() {
    try {
      File file = rolesPath.toFile();
      if (!file.exists()) {
        return new ArrayList<>();
      }
      Role[] roles = objectMapper.readValue(file, Role[].class);
      logger.debug("Loaded {} roles", roles.length);
      return new ArrayList<>(Arrays.asList(roles));
    } catch (JacksonException e) {
      logger.error("Failed to load roles", e);
      return new ArrayList<>();
    }
  }

  /**
   * Saves all roles to file.
   *
   * @param roles list of roles to save
   * @throws IOException if save fails
   */
  public void saveRoles(List<Role> roles) throws IOException {
    objectMapper.writeValue(rolesPath.toFile(), roles);
    setRestrictivePermissions(rolesPath);
    logger.info("Saved {} roles to {}", roles.size(), rolesPath);
  }

  /**
   * Saves a single role. Updates existing or adds new.
   *
   * @param role role to save
   * @throws IOException if save fails
   */
  public void saveRole(Role role) throws IOException {
    List<Role> roles = loadRoles();
    Optional<Role> existing = roles.stream()
        .filter(r -> r.getName().equals(role.getName()))
        .findFirst();

    if (existing.isPresent()) {
      int index = roles.indexOf(existing.get());
      roles.set(index, role);
      logger.info("Updated role: {}", role.getName());
    } else {
      roles.add(role);
      logger.info("Added new role: {}", role.getName());
    }
    saveRoles(roles);
  }

  /**
   * Deletes a role by name.
   *
   * @param name name of role to delete
   * @throws IOException if save fails
   */
  public void deleteRole(String name) throws IOException {
    List<Role> roles = loadRoles();
    roles.removeIf(r -> r.getName().equals(name));
    saveRoles(roles);
    logger.info("Deleted role: {}", name);
  }

  /**
   * Gets a role by name.
   *
   * @param name role name
   * @return optional role
   */
  public Optional<Role> getRole(String name) {
    return loadRoles().stream()
        .filter(r -> r.getName().equals(name))
        .findFirst();
  }

  /**
   * Checks if a role with the given name exists.
   *
   * @param name role name
   * @return true if exists
   */
  public boolean roleExists(String name) {
    return getRole(name).isPresent();
  }

  // ------------------------------------------------------------------
  // Query helpers
  // ------------------------------------------------------------------

  /**
   * Returns all roles that contain the given user.
   *
   * @param username username to look up
   * @return list of roles containing the user
   */
  public List<Role> getRolesForUser(String username) {
    return loadRoles().stream()
        .filter(r -> r.getUserMembers().contains(username))
        .toList();
  }

  /**
   * Returns the union of allowed views across all roles the user
   * belongs to. If the user is not in any role, returns an empty set.
   *
   * @param username username
   * @return set of allowed view labels
   */
  public Set<String> getAllowedViewsForUser(String username) {
    return getRolesForUser(username).stream()
        .flatMap(r -> r.getAllowedViews().stream())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Returns the union of server names across all roles the user
   * belongs to.
   *
   * @param username username
   * @return set of allowed server names
   */
  public Set<String> getAllowedServersForUser(String username) {
    return getRolesForUser(username).stream()
        .flatMap(r -> r.getServerMembers().stream())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Checks whether the user belongs to at least one role.
   *
   * @param username username
   * @return true if the user is a member of any role
   */
  public boolean isUserInAnyRole(String username) {
    return loadRoles().stream()
        .anyMatch(r -> r.getUserMembers().contains(username));
  }

  /**
   * Returns users that would be left without any role if the given
   * role were deleted.
   *
   * @param roleName role to hypothetically remove
   * @return list of users who belong only to that role
   */
  public List<String> getUsersOnlyInRole(String roleName) {
    List<Role> allRoles = loadRoles();
    Optional<Role> target = allRoles.stream()
        .filter(r -> r.getName().equals(roleName))
        .findFirst();
    if (target.isEmpty()) {
      return List.of();
    }
    List<String> usersInTarget = target.get().getUserMembers();
    return usersInTarget.stream()
        .filter(user -> allRoles.stream()
            .filter(r -> !r.getName().equals(roleName))
            .noneMatch(r -> r.getUserMembers().contains(user)))
        .toList();
  }

  // ------------------------------------------------------------------
  // Migration / default roles
  // ------------------------------------------------------------------

  /**
   * Creates default roles by migrating from the existing
   * ADMIN/VIEWER user records (local auth) or creating a single
   * Admin role (oauth / first run).
   */
  private void createMigrationRoles() throws IOException {
    List<String> allServerNames = configurationService
        .loadConfigurations().stream()
        .map(LdapServerConfig::getName)
        .toList();

    List<Role> roles = new ArrayList<>();

    if (userService != null) {
      // Local auth: migrate existing user roles
      List<UserService.UserRecord> users = userService.loadUsers();

      List<String> adminUsers = users.stream()
          .filter(u -> u.roles().contains("ADMIN"))
          .map(UserService.UserRecord::username)
          .toList();

      List<String> viewerUsers = users.stream()
          .filter(u -> u.roles().contains("VIEWER")
              && !u.roles().contains("ADMIN"))
          .map(UserService.UserRecord::username)
          .toList();

      Role adminRole = new Role("Admin");
      adminRole.setAllowedViews(new ArrayList<>(Role.ALL_VIEWS));
      adminRole.setServerMembers(new ArrayList<>(allServerNames));
      adminRole.setUserMembers(new ArrayList<>(adminUsers));
      roles.add(adminRole);

      if (!viewerUsers.isEmpty()) {
        Role viewerRole = new Role("Viewer");
        viewerRole.setAllowedViews(new ArrayList<>(List.of(
            "Browse", "Export", "Schema", "Search")));
        viewerRole.setServerMembers(new ArrayList<>(allServerNames));
        viewerRole.setUserMembers(new ArrayList<>(viewerUsers));
        roles.add(viewerRole);
      }
    } else {
      // OAuth or first run without users
      Role adminRole = new Role("Admin");
      adminRole.setAllowedViews(new ArrayList<>(Role.ALL_VIEWS));
      adminRole.setServerMembers(new ArrayList<>(allServerNames));
      roles.add(adminRole);
    }

    saveRoles(roles);
    logger.info("Created {} default role(s)", roles.size());
  }

  // ------------------------------------------------------------------
  // Internal helpers
  // ------------------------------------------------------------------

  private void ensureDirectoryExists() {
    try {
      Path dir = rolesPath.getParent();
      if (dir != null && !Files.exists(dir)) {
        Files.createDirectories(dir);
        logger.info("Created roles directory: {}", dir);
      }
    } catch (IOException e) {
      logger.error("Failed to create roles directory", e);
    }
  }

  private void setRestrictivePermissions(Path path) {
    try {
      Set<PosixFilePermission> perms = new HashSet<>();
      perms.add(PosixFilePermission.OWNER_READ);
      perms.add(PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(path, perms);
      logger.debug("Set restrictive permissions on: {}", path);
    } catch (UnsupportedOperationException e) {
      logger.debug(
          "POSIX permissions not supported on this system");
    } catch (IOException e) {
      logger.warn(
          "Failed to set restrictive permissions on: {}", path, e);
    }
  }
}

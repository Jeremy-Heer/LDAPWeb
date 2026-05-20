package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.model.Role;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for RoleService.
 */
@DisplayName("RoleService")
class RoleServiceTest {

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("saveRole persists role and query helpers return expected data")
  void saveRoleAndQueryHelpers() throws Exception {
    ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.loadConfigurations()).thenReturn(List.of());

    RoleService roleService = new RoleService(
        tempDir.toString(),
        configurationService,
        Optional.empty(),
        "none");

    Role role = new Role("Operators");
    role.setUserMembers(List.of("alice"));
    role.setServerMembers(List.of("ServerA"));
    role.setAllowedViews(List.of("Browse", "Search"));

    roleService.saveRole(role);

    assertThat(roleService.roleExists("Operators")).isTrue();
    assertThat(roleService.getRolesForUser("alice")).hasSize(1);
    assertThat(roleService.getAllowedServersForUser("alice"))
        .containsExactly("ServerA");
    assertThat(roleService.getAllowedViewsForUser("alice"))
        .containsExactly("Browse", "Search");
    assertThat(roleService.isUserInAnyRole("alice")).isTrue();
  }

  @Test
  @DisplayName("getUsersOnlyInRole returns users orphaned by role deletion")
  void usersOnlyInRole() throws Exception {
    ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.loadConfigurations()).thenReturn(List.of());

    RoleService roleService = new RoleService(
        tempDir.toString(),
        configurationService,
        Optional.empty(),
        "none");

    Role admin = new Role("Admin");
    admin.setUserMembers(List.of("alice", "bob"));

    Role viewer = new Role("Viewer");
    viewer.setUserMembers(List.of("bob"));

    roleService.saveRoles(List.of(admin, viewer));

    assertThat(roleService.getUsersOnlyInRole("Admin"))
        .containsExactly("alice");
    assertThat(roleService.getUsersOnlyInRole("Missing")).isEmpty();
  }

  @Test
  @DisplayName("initDefaultRoles creates Admin role when file is missing")
  void initDefaultRolesCreatesAdminRole() {
    ConfigurationService configurationService = mock(ConfigurationService.class);

    LdapServerConfig serverA = new LdapServerConfig();
    serverA.setName("ServerA");
    LdapServerConfig serverB = new LdapServerConfig();
    serverB.setName("ServerB");
    when(configurationService.loadConfigurations()).thenReturn(List.of(serverA, serverB));

    RoleService roleService = new RoleService(
        tempDir.toString(),
        configurationService,
        Optional.empty(),
        "oauth");

    roleService.initDefaultRoles();

    List<Role> roles = roleService.loadRoles();
    assertThat(roles).hasSize(1);
    Role admin = roles.getFirst();
    assertThat(admin.getName()).isEqualTo("Admin");
    assertThat(Set.copyOf(admin.getServerMembers()))
        .containsExactlyInAnyOrder("ServerA", "ServerB");
    assertThat(admin.getAllowedViews()).containsAll(Role.ALL_VIEWS);
  }
}

package com.ldapbrowser.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ldapbrowser.model.Role;
import com.ldapbrowser.service.RoleService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Unit tests for {@link OidcRoleMappingConfig}.
 * Verifies that all OIDC users receive {@code ROLE_USER} and
 * that new users are auto-assigned to the default role.
 */
@ExtendWith(MockitoExtension.class)
class OidcRoleMappingConfigTest {

  @Mock
  private RoleService roleService;

  // ---------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------

  private OidcUserAuthority oidcAuthority(Map<String, Object> claims) {
    OidcIdToken token = new OidcIdToken(
        "token-value",
        java.time.Instant.now(),
        java.time.Instant.now().plusSeconds(3600),
        claims);
    return new OidcUserAuthority(token);
  }

  private Collection<String> authorityStrings(
      Collection<? extends GrantedAuthority> authorities) {
    return authorities.stream()
        .map(GrantedAuthority::getAuthority)
        .toList();
  }

  // ---------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Authority mapping")
  class AuthorityMapping {

    @Test
    @DisplayName("all OIDC users receive ROLE_USER")
    void grantsRoleUser() {
      when(roleService.isUserInAnyRole("user1")).thenReturn(true);
      OidcRoleMappingConfig config =
          new OidcRoleMappingConfig(roleService, "Admin");
      GrantedAuthoritiesMapper mapper =
          config.oidcAuthoritiesMapper();

      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "user1",
          "preferred_username", "user1"));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(auth));

      assertThat(authorityStrings(result)).contains("ROLE_USER");
    }

    @Test
    @DisplayName("existing authorities are preserved alongside ROLE_USER")
    void preservesExistingAuthorities() {
      when(roleService.isUserInAnyRole("user2")).thenReturn(true);
      OidcRoleMappingConfig config =
          new OidcRoleMappingConfig(roleService, "Admin");
      GrantedAuthoritiesMapper mapper =
          config.oidcAuthoritiesMapper();

      SimpleGrantedAuthority existing =
          new SimpleGrantedAuthority("OIDC_USER");
      OidcUserAuthority oidc = oidcAuthority(Map.of(
          "sub", "user2",
          "preferred_username", "user2"));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(existing, oidc));

      assertThat(authorityStrings(result))
          .contains("OIDC_USER", "ROLE_USER");
    }
  }

  @Nested
  @DisplayName("Auto-assignment to default role")
  class AutoAssignment {

    @Test
    @DisplayName("new user is added to default role")
    void newUserAddedToDefaultRole() throws IOException {
      when(roleService.isUserInAnyRole("newuser")).thenReturn(false);
      Role admin = new Role();
      admin.setName("Admin");
      admin.setUserMembers(new ArrayList<>());
      when(roleService.loadRoles()).thenReturn(List.of(admin));

      OidcRoleMappingConfig config =
          new OidcRoleMappingConfig(roleService, "Admin");
      GrantedAuthoritiesMapper mapper =
          config.oidcAuthoritiesMapper();

      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "newuser",
          "preferred_username", "newuser"));
      mapper.mapAuthorities(Set.of(auth));

      verify(roleService).saveRole(admin);
      assertThat(admin.getUserMembers()).contains("newuser");
    }

    @Test
    @DisplayName("existing user is not re-assigned")
    void existingUserNotReassigned() throws IOException {
      when(roleService.isUserInAnyRole("existing"))
          .thenReturn(true);

      OidcRoleMappingConfig config =
          new OidcRoleMappingConfig(roleService, "Admin");
      GrantedAuthoritiesMapper mapper =
          config.oidcAuthoritiesMapper();

      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "existing",
          "preferred_username", "existing"));
      mapper.mapAuthorities(Set.of(auth));

      verify(roleService, never()).saveRole(any());
    }
  }
}

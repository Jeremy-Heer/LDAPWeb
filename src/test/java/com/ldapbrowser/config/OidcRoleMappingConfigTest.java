package com.ldapbrowser.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ldapbrowser.model.Role;
import com.ldapbrowser.service.RoleService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Unit tests for {@link OidcRoleMappingConfig}.
 * Verifies claim-based authority mapping and secure fallback.
 */
class OidcRoleMappingConfigTest {

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

    private OidcRoleMappingConfig configWithRoles(
            OAuthSecurityProperties properties,
            String... roleNames) {
        RoleService roleService = mock(RoleService.class);
        List<Role> roles = java.util.Arrays.stream(roleNames)
                .map(Role::new)
                .toList();
        when(roleService.loadRoles()).thenReturn(roles);
        return new OidcRoleMappingConfig(properties, roleService);
    }

  // ---------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Authority mapping")
  class AuthorityMapping {

    @Test
    @DisplayName("maps matching claim to APP_ROLE and ROLE_USER")
    void mapsClaimToAppRole() {
      OAuthSecurityProperties props = new OAuthSecurityProperties();
      props.setRoleClaim("roles");
      props.setDefaultRole("DENY");

      OidcRoleMappingConfig config = configWithRoles(
          props,
          "Admin",
          "Viewer");
      GrantedAuthoritiesMapper mapper =
          config.oidcAuthoritiesMapper();

      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "user1",
          "roles", Set.of("Admin")));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(auth));

      assertThat(authorityStrings(result))
          .contains("APP_ROLE:Admin", "ROLE_USER")
          .doesNotContain("ROLE_ADMIN", "ROLE_VIEWER");
    }

    @Test
    @DisplayName("matches roles case-insensitively")
    void matchesRolesCaseInsensitively() {
      OAuthSecurityProperties props = new OAuthSecurityProperties();
      props.setRoleClaim("roles");
      props.setDefaultRole("DENY");

      OidcRoleMappingConfig config = configWithRoles(
          props,
          "Admin");
      GrantedAuthoritiesMapper mapper =
          config.oidcAuthoritiesMapper();

      OidcUserAuthority oidc = oidcAuthority(Map.of(
          "sub", "user2",
          "roles", Set.of("admin")));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(oidc));

      assertThat(authorityStrings(result))
          .contains("APP_ROLE:Admin", "ROLE_USER");
    }

    @Test
    @DisplayName("supports nested role claim paths")
    void supportsNestedRoleClaims() {
      OAuthSecurityProperties props = new OAuthSecurityProperties();
      props.setRoleClaim("realm_access.roles");
      props.setDefaultRole("DENY");

      OidcRoleMappingConfig config = configWithRoles(
          props,
          "Viewer");
      GrantedAuthoritiesMapper mapper =
          config.oidcAuthoritiesMapper();

      OidcUserAuthority oidc = oidcAuthority(Map.of(
          "sub", "user3",
          "realm_access", Map.of("roles", Set.of("Viewer"))));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(oidc));

      assertThat(authorityStrings(result))
          .contains("APP_ROLE:Viewer", "ROLE_USER");
    }

    @Test
    @DisplayName("denies by default when no matching role claim")
    void deniesByDefaultWhenUnmapped() {
      OAuthSecurityProperties props = new OAuthSecurityProperties();
      props.setRoleClaim("roles");
      props.setDefaultRole("DENY");

      OidcRoleMappingConfig config = configWithRoles(
          props,
          "Admin",
          "Viewer");
      GrantedAuthoritiesMapper mapper =
          config.oidcAuthoritiesMapper();

      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "user4",
          "roles", Set.of("some-other-role")));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(auth));

      assertThat(authorityStrings(result))
          .doesNotContain("APP_ROLE:Admin", "APP_ROLE:Viewer", "ROLE_USER");
    }

    @Test
    @DisplayName("fallback can map to existing role name")
    void supportsRoleNameFallback() {
      OAuthSecurityProperties props = new OAuthSecurityProperties();
      props.setRoleClaim("roles");
      props.setDefaultRole("Viewer");

      OidcRoleMappingConfig config = configWithRoles(
          props,
          "Admin",
          "Viewer");
      GrantedAuthoritiesMapper mapper =
          config.oidcAuthoritiesMapper();

      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "user5",
          "roles", Set.of("some-other-role")));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(auth));

      assertThat(authorityStrings(result))
          .contains("APP_ROLE:Viewer", "ROLE_USER")
          .doesNotContain("APP_ROLE:Admin");
    }

    @Test
    @DisplayName("preserves existing authorities")
    void preservesExistingAuthorities() {
      OAuthSecurityProperties props = new OAuthSecurityProperties();
      props.setRoleClaim("roles");
      props.setDefaultRole("DENY");

      OidcRoleMappingConfig config = configWithRoles(
          props,
          "Viewer");
      GrantedAuthoritiesMapper mapper =
          config.oidcAuthoritiesMapper();

      SimpleGrantedAuthority existing =
          new SimpleGrantedAuthority("OIDC_USER");
      OidcUserAuthority oidc = oidcAuthority(Map.of(
          "sub", "user6",
          "roles", Set.of("Viewer")));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(existing, oidc));

      assertThat(authorityStrings(result))
          .contains("OIDC_USER", "APP_ROLE:Viewer", "ROLE_USER");
    }
  }
}

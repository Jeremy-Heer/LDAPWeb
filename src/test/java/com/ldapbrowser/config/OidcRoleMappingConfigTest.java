package com.ldapbrowser.config;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Verifies that OIDC token claims are correctly mapped to
 * {@code ROLE_ADMIN} and {@code ROLE_VIEWER}.
 */
class OidcRoleMappingConfigTest {

  private static final String ROLE_CLAIM = "roles";
  private static final String ADMIN_CLAIM_VALUE = "ldap-admin";
  private static final String VIEWER_CLAIM_VALUE = "ldap-viewer";
  private static final String DEFAULT_ROLE = "VIEWER";

  private final OidcRoleMappingConfig config = new OidcRoleMappingConfig(
      ROLE_CLAIM, ADMIN_CLAIM_VALUE, VIEWER_CLAIM_VALUE, DEFAULT_ROLE);

  private final GrantedAuthoritiesMapper mapper = config.oidcAuthoritiesMapper();

  // ---------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------

  /**
   * Creates an OIDC authority with the given claims map.
   */
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
  @DisplayName("Flat claim mapping")
  class FlatClaim {

    @Test
    @DisplayName("admin claim maps to ROLE_ADMIN")
    void adminRole() {
      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "user1",
          ROLE_CLAIM, List.of(ADMIN_CLAIM_VALUE)));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(auth));

      assertThat(authorityStrings(result)).contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("viewer claim maps to ROLE_VIEWER")
    void viewerRole() {
      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "user2",
          ROLE_CLAIM, List.of(VIEWER_CLAIM_VALUE)));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(auth));

      assertThat(authorityStrings(result)).contains("ROLE_VIEWER");
    }

    @Test
    @DisplayName("both admin and viewer claims produce both roles")
    void bothRoles() {
      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "user3",
          ROLE_CLAIM, List.of(ADMIN_CLAIM_VALUE, VIEWER_CLAIM_VALUE)));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(auth));

      assertThat(authorityStrings(result))
          .contains("ROLE_ADMIN", "ROLE_VIEWER");
    }

    @Test
    @DisplayName("string claim value (not list) is handled")
    void stringClaim() {
      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "user4",
          ROLE_CLAIM, ADMIN_CLAIM_VALUE));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(auth));

      assertThat(authorityStrings(result)).contains("ROLE_ADMIN");
    }
  }

  @Nested
  @DisplayName("Default role")
  class DefaultRole {

    @Test
    @DisplayName("missing role claim assigns default role")
    void noClaimUsesDefault() {
      OidcUserAuthority auth = oidcAuthority(Map.of("sub", "user5"));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(auth));

      assertThat(authorityStrings(result)).contains("ROLE_VIEWER");
    }

    @Test
    @DisplayName("unrecognised claim value assigns default role")
    void unknownClaimUsesDefault() {
      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "user6",
          ROLE_CLAIM, List.of("some-other-role")));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(auth));

      assertThat(authorityStrings(result)).contains("ROLE_VIEWER");
    }
  }

  @Nested
  @DisplayName("Nested claim mapping")
  class NestedClaim {

    @Test
    @DisplayName("dot-separated claim path extracts nested roles")
    void nestedKeycloakStyle() {
      OidcRoleMappingConfig nestedConfig = new OidcRoleMappingConfig(
          "realm_access.roles", ADMIN_CLAIM_VALUE,
          VIEWER_CLAIM_VALUE, DEFAULT_ROLE);
      GrantedAuthoritiesMapper nestedMapper =
          nestedConfig.oidcAuthoritiesMapper();

      OidcUserAuthority auth = oidcAuthority(Map.of(
          "sub", "user7",
          "realm_access", Map.of("roles",
              List.of(ADMIN_CLAIM_VALUE))));
      Collection<? extends GrantedAuthority> result =
          nestedMapper.mapAuthorities(Set.of(auth));

      assertThat(authorityStrings(result)).contains("ROLE_ADMIN");
    }
  }

  @Nested
  @DisplayName("Original authorities preserved")
  class OriginalAuthorities {

    @Test
    @DisplayName("existing authorities are kept alongside mapped roles")
    void preservesExisting() {
      SimpleGrantedAuthority existing =
          new SimpleGrantedAuthority("OIDC_USER");
      OidcUserAuthority oidc = oidcAuthority(Map.of(
          "sub", "user8",
          ROLE_CLAIM, List.of(ADMIN_CLAIM_VALUE)));
      Collection<? extends GrantedAuthority> result =
          mapper.mapAuthorities(Set.of(existing, oidc));

      assertThat(authorityStrings(result))
          .contains("OIDC_USER", "ROLE_ADMIN");
    }
  }
}

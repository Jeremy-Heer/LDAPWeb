package com.ldapbrowser.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Maps OIDC token claims to Spring Security granted authorities.
 *
 * <p>Configuration properties (all have sensible defaults):
 * <ul>
 *   <li>{@code ldapbrowser.oauth.role-claim} &ndash;
 *       claim containing role names (default {@code roles})</li>
 *   <li>{@code ldapbrowser.oauth.admin-role} &ndash;
 *       claim value mapped to {@code ROLE_ADMIN} (default {@code ldap-admin})</li>
 *   <li>{@code ldapbrowser.oauth.viewer-role} &ndash;
 *       claim value mapped to {@code ROLE_VIEWER} (default {@code ldap-viewer})</li>
 *   <li>{@code ldapbrowser.oauth.default-role} &ndash;
 *       role assigned when no matching claim is found (default {@code VIEWER})</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "ldapbrowser.auth.mode", havingValue = "oauth")
public class OidcRoleMappingConfig {

  private static final Logger logger =
      LoggerFactory.getLogger(OidcRoleMappingConfig.class);

  private final String roleClaim;
  private final String adminRole;
  private final String viewerRole;
  private final String defaultRole;

  /**
   * Constructs the OIDC role mapping configuration.
   *
   * @param roleClaim OIDC claim name containing roles
   * @param adminRole claim value that maps to ADMIN
   * @param viewerRole claim value that maps to VIEWER
   * @param defaultRole fallback role when no claim matches
   */
  public OidcRoleMappingConfig(
      @Value("${ldapbrowser.oauth.role-claim:roles}") String roleClaim,
      @Value("${ldapbrowser.oauth.admin-role:ldap-admin}") String adminRole,
      @Value("${ldapbrowser.oauth.viewer-role:ldap-viewer}") String viewerRole,
      @Value("${ldapbrowser.oauth.default-role:VIEWER}") String defaultRole) {
    this.roleClaim = roleClaim;
    this.adminRole = adminRole;
    this.viewerRole = viewerRole;
    this.defaultRole = defaultRole;
    logger.info("OIDC role mapping: claim={}, admin={}, viewer={}, default={}",
        roleClaim, adminRole, viewerRole, defaultRole);
  }

  /**
   * Creates a {@link GrantedAuthoritiesMapper} that converts OIDC
   * token claims into {@code ROLE_ADMIN} or {@code ROLE_VIEWER}.
   *
   * @return authorities mapper bean
   */
  @Bean
  public GrantedAuthoritiesMapper oidcAuthoritiesMapper() {
    return authorities -> {
      Set<GrantedAuthority> mapped = new HashSet<>();

      for (GrantedAuthority authority : authorities) {
        mapped.add(authority); // preserve original

        if (authority instanceof OidcUserAuthority oidcAuthority) {
          Map<String, Object> claims =
              oidcAuthority.getIdToken().getClaims();
          mapped.addAll(extractRoles(claims));
        }
      }

      return mapped;
    };
  }

  /**
   * Extracts application roles from OIDC claims.
   *
   * @param claims ID-token claims map
   * @return set of granted authorities
   */
  private Set<GrantedAuthority> extractRoles(Map<String, Object> claims) {
    Set<GrantedAuthority> roles = new HashSet<>();

    Object claimValue = claims.get(roleClaim);

    if (claimValue instanceof Collection<?> claimList) {
      for (Object item : claimList) {
        String role = item.toString();
        if (adminRole.equals(role)) {
          roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        } else if (viewerRole.equals(role)) {
          roles.add(new SimpleGrantedAuthority("ROLE_VIEWER"));
        }
      }
    } else if (claimValue instanceof String role) {
      if (adminRole.equals(role)) {
        roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
      } else if (viewerRole.equals(role)) {
        roles.add(new SimpleGrantedAuthority("ROLE_VIEWER"));
      }
    }

    // Nested claim support (e.g. Keycloak realm_access.roles)
    if (roles.isEmpty() && roleClaim.contains(".")) {
      roles.addAll(extractNestedRoles(claims));
    }

    // Apply default role when nothing matched
    if (roles.isEmpty()) {
      logger.debug("No matching role claim found, assigning default: {}",
          defaultRole);
      roles.add(new SimpleGrantedAuthority("ROLE_" + defaultRole));
    }

    return roles;
  }

  /**
   * Handles dot-separated claim paths like
   * {@code realm_access.roles} for Keycloak.
   */
  @SuppressWarnings("unchecked")
  private Set<GrantedAuthority> extractNestedRoles(
      Map<String, Object> claims) {
    Set<GrantedAuthority> roles = new HashSet<>();
    String[] parts = roleClaim.split("\\.");
    Object current = claims;

    for (String part : parts) {
      if (current instanceof Map<?, ?> map) {
        current = map.get(part);
      } else {
        return roles;
      }
    }

    if (current instanceof Collection<?> list) {
      for (Object item : list) {
        String role = item.toString();
        if (adminRole.equals(role)) {
          roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        } else if (viewerRole.equals(role)) {
          roles.add(new SimpleGrantedAuthority("ROLE_VIEWER"));
        }
      }
    }

    return roles;
  }
}

package com.ldapbrowser.config;

import com.ldapbrowser.model.Role;
import com.ldapbrowser.service.RoleService;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Maps OIDC token claims to Spring Security granted authorities.
 *
 * <p>All authenticated OIDC users receive {@code ROLE_USER}.
 * Fine-grained view and server access is controlled by
 * {@link RoleService}. When an OIDC user logs in for the first
 * time and is not a member of any role, they are automatically
 * assigned to the configured default role.
 *
 * <p>Configuration properties:
 * <ul>
 *   <li>{@code ldapbrowser.oauth.default-role-name} &ndash;
 *       role name to auto-assign new users to
 *       (default {@code Admin})</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "ldapbrowser.auth.mode", havingValue = "oauth")
public class OidcRoleMappingConfig {

  private static final Logger logger =
      LoggerFactory.getLogger(OidcRoleMappingConfig.class);

  private final RoleService roleService;
  private final String defaultRoleName;

  /**
   * Constructs the OIDC role mapping configuration.
   *
   * @param roleService role service (lazy to avoid circular init)
   * @param defaultRoleName default role name for new OIDC users
   */
  public OidcRoleMappingConfig(
      @Lazy RoleService roleService,
      @Value("${ldapbrowser.oauth.default-role-name:Admin}")
          String defaultRoleName) {
    this.roleService = roleService;
    this.defaultRoleName = defaultRoleName;
    logger.info("OIDC role mapping: default-role-name={}",
        defaultRoleName);
  }

  /**
   * Creates a {@link GrantedAuthoritiesMapper} that grants
   * {@code ROLE_USER} to all authenticated OIDC users and
   * auto-assigns them to the default role if they have none.
   *
   * @return authorities mapper bean
   */
  @Bean
  public GrantedAuthoritiesMapper oidcAuthoritiesMapper() {
    return authorities -> {
      Set<GrantedAuthority> mapped = new HashSet<>();
      mapped.add(new SimpleGrantedAuthority("ROLE_USER"));

      for (GrantedAuthority authority : authorities) {
        mapped.add(authority); // preserve original

        if (authority instanceof OidcUserAuthority oidcAuthority) {
          String username = oidcAuthority.getIdToken()
              .getPreferredUsername();
          if (username == null) {
            username = oidcAuthority.getIdToken().getSubject();
          }
          ensureUserInRole(username);
        }
      }

      return mapped;
    };
  }

  /**
   * Ensures the OIDC user is a member of at least one role.
   * If not, adds them to the configured default role.
   *
   * @param username OIDC username or subject
   */
  private void ensureUserInRole(String username) {
    if (username == null || username.isBlank()) {
      return;
    }
    if (roleService.isUserInAnyRole(username)) {
      return;
    }
    try {
      List<Role> roles = roleService.loadRoles();
      Role target = roles.stream()
          .filter(r -> r.getName().equals(defaultRoleName))
          .findFirst()
          .orElse(null);

      if (target != null) {
        target.getUserMembers().add(username);
        roleService.saveRole(target);
        logger.info(
            "Auto-assigned OIDC user '{}' to role '{}'",
            username, defaultRoleName);
      } else {
        logger.warn(
            "Default role '{}' not found; OIDC user '{}' has no role",
            defaultRoleName, username);
      }
    } catch (IOException e) {
      logger.error(
          "Failed to auto-assign OIDC user to default role", e);
    }
  }
}

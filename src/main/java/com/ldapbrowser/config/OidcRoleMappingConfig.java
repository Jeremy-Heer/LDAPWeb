package com.ldapbrowser.config;

import com.ldapbrowser.model.Role;
import com.ldapbrowser.service.RoleService;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Authorities are derived from claim values in the configured
 * OIDC token claim path. No role-file auto-assignment occurs in
 * OAuth mode.
 *
 * <p>Configuration properties:
 * <ul>
 *   <li>{@code ldapbrowser.oauth.role-claim} &ndash; claim path
 *       containing role values (supports nested dot paths)</li>
 *   <li>{@code ldapbrowser.oauth.default-role} &ndash; fallback
 *       app role name or {@code DENY}</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "ldapbrowser.auth.mode", havingValue = "oauth")
public class OidcRoleMappingConfig {

  private static final Logger logger =
      LoggerFactory.getLogger(OidcRoleMappingConfig.class);
  private static final String APP_ROLE_PREFIX = "APP_ROLE:";
  private static final String DENY_FALLBACK = "DENY";

  private final OAuthSecurityProperties oauthProperties;
  private final RoleService roleService;

  /**
   * Constructs the OIDC role mapping configuration.
   *
   * @param oauthProperties typed oauth security properties
   * @param roleService application role service
   */
  public OidcRoleMappingConfig(
      OAuthSecurityProperties oauthProperties,
      RoleService roleService) {
    this.oauthProperties = oauthProperties;
    this.roleService = roleService;
    logger.info(
        "OIDC role mapping configured: role-claim='{}', default-role='{}'",
        oauthProperties.getRoleClaim(),
        oauthProperties.getDefaultRole());
  }

  /**
   * Creates a {@link GrantedAuthoritiesMapper} that derives
    * {@code APP_ROLE:<RoleName>} and {@code ROLE_USER}
    * from configured OIDC claim values.
   *
   * @return authorities mapper bean
   */
  @Bean
  public GrantedAuthoritiesMapper oidcAuthoritiesMapper() {
    return authorities -> {
      Set<GrantedAuthority> mapped = new HashSet<>();

      for (GrantedAuthority authority : authorities) {
        String rawAuthority = authority.getAuthority();
        if (!"ROLE_USER".equals(rawAuthority)
            && (rawAuthority == null
                || !rawAuthority.startsWith(APP_ROLE_PREFIX))) {
          mapped.add(authority); // preserve non-role authorities
        }

        if (authority instanceof OidcUserAuthority oidcAuthority) {
          Set<String> claimValues = extractClaimValues(
              oidcAuthority.getIdToken().getClaims(),
              oauthProperties.getRoleClaim());
          logger.debug(
              "OIDC role mapping extracted {} claim value(s) from path '{}': {}",
              claimValues.size(),
              oauthProperties.getRoleClaim(),
              claimValues);
          applyMappedAuthorities(mapped, claimValues);
        }
      }

      return mapped;
    };
  }

  private void applyMappedAuthorities(
      Set<GrantedAuthority> mapped,
      Set<String> claimValues) {
    Set<String> matchedRoleNames = resolveAppRoleNames(claimValues);
    if (!matchedRoleNames.isEmpty()) {
      matchedRoleNames.forEach(roleName ->
          mapped.add(new SimpleGrantedAuthority(APP_ROLE_PREFIX + roleName)));
      mapped.add(new SimpleGrantedAuthority("ROLE_USER"));

      Set<String> unmatched = new LinkedHashSet<>(claimValues);
      unmatched.removeAll(matchedRoleNames);
      logger.info(
          "OIDC role mapping resolved {} app role(s): matched={}, unmatched={}.",
          matchedRoleNames.size(),
          matchedRoleNames,
          unmatched);
      return;
    }

    String fallback = oauthProperties.getDefaultRole();
    String matchedFallbackRole = resolveSingleRoleName(fallback);
    if (matchedFallbackRole != null) {
      mapped.add(new SimpleGrantedAuthority(APP_ROLE_PREFIX + matchedFallbackRole));
      mapped.add(new SimpleGrantedAuthority("ROLE_USER"));
      logger.warn(
          "OIDC role claim had no mapping; applying fallback role '{}'",
          matchedFallbackRole);
      return;
    }

    if (DENY_FALLBACK.equals(normalize(fallback))) {
      logger.warn(
          "OIDC role claim had no mapping; default-role=DENY enforces no app roles");
      return;
    }

    logger.warn(
        "OIDC role claim had no mapping and fallback role '{}' was not found; denying app access",
        fallback);
  }

  private Set<String> resolveAppRoleNames(Set<String> claimValues) {
    Map<String, String> normalizedRoleNameIndex = buildNormalizedRoleNameIndex();
    Set<String> matchedRoleNames = new LinkedHashSet<>();

    for (String claimValue : claimValues) {
      String roleName = normalizedRoleNameIndex.get(normalize(claimValue));
      if (roleName != null) {
        matchedRoleNames.add(roleName);
      }
    }
    return matchedRoleNames;
  }

  private String resolveSingleRoleName(String roleNameCandidate) {
    if (roleNameCandidate == null || roleNameCandidate.isBlank()) {
      return null;
    }
    Map<String, String> normalizedRoleNameIndex = buildNormalizedRoleNameIndex();
    return normalizedRoleNameIndex.get(normalize(roleNameCandidate));
  }

  private Map<String, String> buildNormalizedRoleNameIndex() {
    Map<String, String> roleNameIndex = new LinkedHashMap<>();
    for (Role role : roleService.loadRoles()) {
      if (role.getName() == null || role.getName().isBlank()) {
        continue;
      }
      roleNameIndex.putIfAbsent(normalize(role.getName()), role.getName());
    }
    return roleNameIndex;
  }

  private Set<String> extractClaimValues(
      Map<String, Object> claims,
      String claimPath) {
    Set<String> values = new HashSet<>();
    if (claims == null || claimPath == null || claimPath.isBlank()) {
      return values;
    }

    Object current = claims;
    for (String part : claimPath.split("\\.")) {
      if (!(current instanceof Map<?, ?> mapCurrent)) {
        return values;
      }
      current = mapCurrent.get(part);
      if (current == null) {
        return values;
      }
    }

    flattenClaimValue(current, values);
    return values;
  }

  private void flattenClaimValue(Object value, Set<String> out) {
    if (value == null) {
      return;
    }
    if (value instanceof String s) {
      if (!s.isBlank()) {
        out.add(s);
      }
      return;
    }
    if (value instanceof Collection<?> collection) {
      for (Object item : collection) {
        flattenClaimValue(item, out);
      }
      return;
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        flattenClaimValue(Array.get(value, i), out);
      }
      return;
    }
    out.add(String.valueOf(value));
  }

  private String normalize(String value) {
    return value == null
        ? ""
        : value.trim().toUpperCase(Locale.ROOT);
  }
}

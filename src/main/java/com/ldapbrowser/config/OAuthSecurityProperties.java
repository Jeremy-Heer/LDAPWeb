package com.ldapbrowser.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OAuth security properties for claim mapping and optional mTLS.
 */
@Component
@ConfigurationProperties(prefix = "ldapbrowser.oauth")
public class OAuthSecurityProperties {

  private String roleClaim = "roles";
  private String adminRole = "ldap-admin";
  private String viewerRole = "ldap-viewer";
  private String defaultRole = "DENY";
  private Mtls mtls = new Mtls();

  public String getRoleClaim() {
    return roleClaim;
  }

  public void setRoleClaim(String roleClaim) {
    this.roleClaim = roleClaim;
  }

  public String getAdminRole() {
    return adminRole;
  }

  public void setAdminRole(String adminRole) {
    this.adminRole = adminRole;
  }

  public String getViewerRole() {
    return viewerRole;
  }

  public void setViewerRole(String viewerRole) {
    this.viewerRole = viewerRole;
  }

  public String getDefaultRole() {
    return defaultRole;
  }

  public void setDefaultRole(String defaultRole) {
    this.defaultRole = defaultRole;
  }

  public Mtls getMtls() {
    return mtls;
  }

  public void setMtls(Mtls mtls) {
    this.mtls = mtls;
  }

  /**
   * Optional mTLS settings for OAuth token endpoint calls.
   */
  public static class Mtls {

    private boolean enabled;
    private String sslBundle;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getSslBundle() {
      return sslBundle;
    }

    public void setSslBundle(String sslBundle) {
      this.sslBundle = sslBundle;
    }
  }
}
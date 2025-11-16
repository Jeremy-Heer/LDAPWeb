package com.ldapbrowser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.Objects;

/**
 * Model class representing LDAP server connection configuration.
 * Used for storing and managing LDAP server connection details.
 */
public class LdapServerConfig implements Serializable {

  private static final long serialVersionUID = 1L;

  private String name;
  private String host;
  private int port = 389;
  private String baseDn;
  private String bindDn;
  private String bindPassword;
  private boolean useSsl;
  private boolean useStartTls;
  private boolean validateCertificate = true;

  /**
   * Default constructor.
   */
  public LdapServerConfig() {
  }

  /**
   * Constructor with all fields.
   *
   * @param name server name
   * @param host server host
   * @param port server port
   * @param baseDn base DN
   * @param bindDn bind DN
   * @param bindPassword bind password
   * @param useSsl use SSL
   * @param useStartTls use StartTLS
   */
  public LdapServerConfig(String name, String host, int port, String baseDn,
                          String bindDn, String bindPassword,
                          boolean useSsl, boolean useStartTls) {
    this.name = name;
    this.host = host;
    this.port = port;
    this.baseDn = baseDn;
    this.bindDn = bindDn;
    this.bindPassword = bindPassword;
    this.useSsl = useSsl;
    this.useStartTls = useStartTls;
    this.validateCertificate = true; // Default to validating certificates
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getBaseDn() {
    return baseDn;
  }

  public void setBaseDn(String baseDn) {
    this.baseDn = baseDn;
  }

  public String getBindDn() {
    return bindDn;
  }

  public void setBindDn(String bindDn) {
    this.bindDn = bindDn;
  }

  public String getBindPassword() {
    return bindPassword;
  }

  public void setBindPassword(String bindPassword) {
    this.bindPassword = bindPassword;
  }

  public boolean isUseSsl() {
    return useSsl;
  }

  public void setUseSsl(boolean useSsl) {
    this.useSsl = useSsl;
  }

  public boolean isUseStartTls() {
    return useStartTls;
  }

  public void setUseStartTls(boolean useStartTls) {
    this.useStartTls = useStartTls;
  }

  public boolean isValidateCertificate() {
    return validateCertificate;
  }

  public void setValidateCertificate(boolean validateCertificate) {
    this.validateCertificate = validateCertificate;
  }

  /**
   * Gets the connection URL for display purposes.
   *
   * @return connection URL string
   */
  @JsonIgnore
  public String getConnectionUrl() {
    String protocol = useSsl ? "ldaps" : "ldap";
    return String.format("%s://%s:%d", protocol, host, port);
  }

  /**
   * Creates a copy of this configuration.
   *
   * @return new instance with same values
   */
  public LdapServerConfig copy() {
    LdapServerConfig copied = new LdapServerConfig(
        this.name + " (Copy)",
        this.host,
        this.port,
        this.baseDn,
        this.bindDn,
        this.bindPassword,
        this.useSsl,
        this.useStartTls
    );
    copied.setValidateCertificate(this.validateCertificate);
    return copied;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LdapServerConfig that = (LdapServerConfig) o;
    return Objects.equals(name, that.name) && Objects.equals(host, that.host);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, host);
  }

  @Override
  public String toString() {
    return String.format("LdapServerConfig{name='%s', host='%s', port=%d}", name, host, port);
  }
}

package com.ldapbrowser.service;

import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import org.slf4j.Logger;

/**
 * Encapsulates conditional bind behavior used by connection tests.
 */
class LdapConnectionBindHelper {

  /**
   * Performs bind when credentials are configured.
   *
   * @param connection LDAP connection to bind
   * @param config server configuration containing credentials
   * @param logger logger used for success message
   * @throws LDAPException if bind fails
   */
  void bindIfConfigured(LDAPConnection connection, LdapServerConfig config, Logger logger)
      throws LDAPException {
    if (config.getBindDn() != null && !config.getBindDn().isEmpty()) {
      connection.bind(config.getBindDn(), config.getBindPassword());
      logger.info("Successfully bound to {} as {}", config.getName(), config.getBindDn());
    }
  }
}
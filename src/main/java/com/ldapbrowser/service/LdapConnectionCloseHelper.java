package com.ldapbrowser.service;

import com.unboundid.ldap.sdk.LDAPConnection;

/**
 * Centralizes LDAP connection close handling.
 */
class LdapConnectionCloseHelper {

  /**
   * Closes an LDAP connection when present.
   *
   * @param connection LDAP connection that may be null
   */
  void closeQuietly(LDAPConnection connection) {
    if (connection != null) {
      connection.close();
    }
  }
}
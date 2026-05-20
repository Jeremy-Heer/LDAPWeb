package com.ldapbrowser.service;

import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.SSLUtil;
import java.security.GeneralSecurityException;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;

/**
 * Creates socket factories and applies StartTLS for LDAP connection bootstrap.
 */
class LdapConnectionBootstrapHelper {

  /**
   * Creates a socket factory from SSLUtil.
   *
   * @param sslUtil configured SSL utility
   * @return socket factory for SSL connections
   * @throws GeneralSecurityException if SSL context creation fails
   */
  SocketFactory createSocketFactory(SSLUtil sslUtil) throws GeneralSecurityException {
    SSLContext sslContext = sslUtil.createSSLContext();
    return sslContext.getSocketFactory();
  }

  /**
   * Applies StartTLS if the server configuration requires it.
   *
   * @param connection LDAP connection
   * @param config server configuration
   * @param sslUtil configured SSL utility
   * @throws LDAPException if StartTLS negotiation fails
   * @throws GeneralSecurityException if SSL context creation fails
   */
  void applyStartTlsIfNeeded(LDAPConnection connection, LdapServerConfig config,
      SSLUtil sslUtil) throws LDAPException, GeneralSecurityException {
    if (config.isUseStartTls() && !config.isUseSsl()) {
      SSLContext sslContext = sslUtil.createSSLContext();
      connection.processExtendedOperation(new StartTLSExtendedRequest(sslContext));
    }
  }
}
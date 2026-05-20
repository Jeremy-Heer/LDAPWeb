package com.ldapbrowser.service;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPConnectionPoolHealthCheck;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;

/**
 * Helper for LDAP connection pool health check behavior.
 */
final class LdapConnectionHealthSupport {

  private LdapConnectionHealthSupport() {
    // Utility class.
  }

  static LDAPConnectionPoolHealthCheck createPoolHealthCheck() {
    return new LDAPConnectionPoolHealthCheck() {
      @Override
      public void ensureNewConnectionValid(LDAPConnection connection) throws LDAPException {
        connection.getRootDSE();
      }

      @Override
      public void ensureConnectionValidAfterException(LDAPConnection connection,
          LDAPException exception) throws LDAPException {
        ResultCode resultCode = exception.getResultCode();
        if (resultCode == ResultCode.SERVER_DOWN
            || resultCode == ResultCode.CONNECT_ERROR
            || resultCode == ResultCode.UNAVAILABLE
            || resultCode == ResultCode.TIMEOUT
            || resultCode == ResultCode.DECODING_ERROR) {
          throw exception;
        }
        connection.getRootDSE();
      }

      @Override
      public void ensureConnectionValidForCheckout(LDAPConnection connection)
          throws LDAPException {
        ensureConnected(connection);
      }

      @Override
      public void ensureConnectionValidForRelease(LDAPConnection connection)
          throws LDAPException {
        ensureConnected(connection);
      }

      @Override
      public void ensureConnectionValidForContinuedUse(LDAPConnection connection)
          throws LDAPException {
        ensureConnected(connection);
      }

      private void ensureConnected(LDAPConnection connection) throws LDAPException {
        if (!connection.isConnected()) {
          throw new LDAPException(ResultCode.SERVER_DOWN, "Connection is not established");
        }
      }
    };
  }

  static boolean isPoolHealthy(LDAPConnectionPool pool) {
    if (pool == null || pool.isClosed()) {
      return false;
    }

    try {
      LDAPConnection conn = pool.getConnection();
      try {
        conn.getRootDSE();
        pool.releaseConnection(conn);
        return true;
      } catch (LDAPException e) {
        pool.releaseDefunctConnection(conn);
        return false;
      }
    } catch (LDAPException e) {
      return false;
    }
  }
}
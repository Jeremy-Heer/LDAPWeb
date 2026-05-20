package com.ldapbrowser.service;

import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import java.security.GeneralSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes LDAP operations with retry behavior for connection-level failures.
 */
class LdapOperationExecutor {

  private static final Logger logger = LoggerFactory.getLogger(LdapOperationExecutor.class);

  /**
   * Executes an LDAP operation and retries once with a recreated pool for
   * connection-level errors.
   */
  <T> T executeWithRetry(
      LdapServerConfig config,
      PoolSupplier poolSupplier,
      LdapOperation<T> operation,
      PoolSupplier poolRecreator
  ) throws LDAPException, GeneralSecurityException {
    try {
      return operation.execute(poolSupplier.get());
    } catch (LDAPException e) {
      if (isConnectionRelatedError(e.getResultCode())) {
        logger.warn("LDAP operation failed with {}, attempting retry after pool recreation",
            e.getResultCode());
        try {
          T result = operation.execute(poolRecreator.get());
          logger.info("LDAP operation succeeded after retry for {}", config.getName());
          return result;
        } catch (LDAPException retryException) {
          logger.error("LDAP operation failed after retry for {}: {}",
              config.getName(), retryException.getMessage());
          throw retryException;
        }
      }
      throw e;
    }
  }

  boolean isConnectionRelatedError(ResultCode resultCode) {
    return resultCode == ResultCode.SERVER_DOWN
        || resultCode == ResultCode.CONNECT_ERROR
        || resultCode == ResultCode.UNAVAILABLE
        || resultCode == ResultCode.TIMEOUT
        || resultCode == ResultCode.DECODING_ERROR;
  }

  @FunctionalInterface
  interface PoolSupplier {
    com.unboundid.ldap.sdk.LDAPConnectionPool get() throws LDAPException, GeneralSecurityException;
  }

  @FunctionalInterface
  interface LdapOperation<T> {
    T execute(com.unboundid.ldap.sdk.LDAPConnectionPool pool) throws LDAPException;
  }
}
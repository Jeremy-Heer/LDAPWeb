package com.ldapbrowser.service;

import com.ldapbrowser.exception.CertificateValidationException;
import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.schema.Schema;
import java.security.GeneralSecurityException;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * Coordinates LDAP connection pool acquisition, validation, and schema caching.
 */
final class LdapConnectionPoolManager {

  @FunctionalInterface
  interface PoolCreator {
    LDAPConnectionPool create(LdapServerConfig config)
        throws LDAPException, GeneralSecurityException, CertificateValidationException;
  }

  @FunctionalInterface
  interface SchemaFetcher {
    Schema fetch(LdapServerConfig config) throws LDAPException;
  }

  private final LdapConnectionPoolStore poolStore;
  private final LdapSchemaCacheManager schemaCacheManager;
  private final Logger logger;
  private final PoolCreator poolCreator;
  private final SchemaFetcher schemaFetcher;
  private final Consumer<String> serverAccessChecker;

  LdapConnectionPoolManager(LdapConnectionPoolStore poolStore,
      LdapSchemaCacheManager schemaCacheManager,
      Logger logger,
      PoolCreator poolCreator,
      SchemaFetcher schemaFetcher,
      Consumer<String> serverAccessChecker) {
    this.poolStore = poolStore;
    this.schemaCacheManager = schemaCacheManager;
    this.logger = logger;
    this.poolCreator = poolCreator;
    this.schemaFetcher = schemaFetcher;
    this.serverAccessChecker = serverAccessChecker;
  }

  LDAPConnectionPool getConnectionPoolForOperation(LdapServerConfig config,
      boolean validatePoolHealth)
      throws LDAPException, GeneralSecurityException {
    serverAccessChecker.accept(config.getName());
    String key = config.getName();
    boolean isNewConnection = !poolStore.hasPool(key);

    poolStore.rememberConfig(key, config);

    LDAPConnectionPool pool;
    try {
      pool = poolStore.computeIfAbsent(key, k -> {
        try {
          return poolCreator.create(config);
        } catch (CertificateValidationException e) {
          throw new RuntimeException("CERT_VALIDATION_FAILED", e);
        } catch (LDAPException | GeneralSecurityException e) {
          logger.error("Failed to create connection pool for {}", config.getName(), e);
          throw new RuntimeException("Failed to create connection pool", e);
        }
      });
    } catch (RuntimeException e) {
      throw e;
    }

    if (!isNewConnection && validatePoolHealth && !LdapConnectionHealthSupport.isPoolHealthy(pool)) {
      logger.warn("Connection pool for {} is unhealthy, recreating", config.getName());
      pool.close();
      poolStore.removePool(key);
      schemaCacheManager.invalidate(key);
      try {
        pool = poolCreator.create(config);
        poolStore.putPool(key, pool);
        isNewConnection = true;
      } catch (CertificateValidationException e) {
        logger.error("Certificate validation failed for {}", config.getName());
        throw new RuntimeException("CERT_VALIDATION_FAILED", e);
      } catch (LDAPException | GeneralSecurityException e) {
        logger.error("Failed to recreate connection pool for {}", config.getName(), e);
        throw new RuntimeException("Failed to recreate connection pool", e);
      }
    }

    if (isNewConnection && schemaCacheManager.getIfPresent(key) == null) {
      try {
        Schema schema = schemaFetcher.fetch(config);
        schemaCacheManager.put(key, schema);
        logger.debug("Pre-fetched and cached schema for {}", config.getName());
      } catch (LDAPException e) {
        logger.warn("Failed to pre-fetch schema for {}: {}", config.getName(), e.getMessage());
      }
    }

    return pool;
  }

  LDAPConnectionPool recreateConnectionPool(LdapServerConfig config)
      throws LDAPException, GeneralSecurityException {
    String key = config.getName();
    LDAPConnectionPool oldPool = poolStore.removePool(key);
    if (oldPool != null) {
      oldPool.close();
    }
    schemaCacheManager.invalidate(key);
    return getConnectionPoolForOperation(config, true);
  }
}
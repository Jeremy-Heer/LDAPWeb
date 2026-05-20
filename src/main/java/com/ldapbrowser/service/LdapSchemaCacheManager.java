package com.ldapbrowser.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.schema.Schema;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Encapsulates schema caching and refresh behavior.
 */
final class LdapSchemaCacheManager {

  @FunctionalInterface
  interface SchemaFetcher {
    Schema fetch(LdapServerConfig config) throws LDAPException;
  }

  private final Cache<String, Schema> schemaCache = Caffeine.newBuilder()
      .maximumSize(500)
      .expireAfterAccess(Duration.ofHours(1))
      .build();

  private final Logger logger;

  LdapSchemaCacheManager(Logger logger) {
    this.logger = logger;
  }

  Schema getSchema(LdapServerConfig config, SchemaFetcher fetcher) throws LDAPException {
    String cacheKey = config.getName();
    Schema cachedSchema = schemaCache.getIfPresent(cacheKey);
    if (cachedSchema != null) {
      return cachedSchema;
    }

    Schema schema = fetcher.fetch(config);
    schemaCache.put(cacheKey, schema);
    return schema;
  }

  Schema refreshSchema(LdapServerConfig config, SchemaFetcher fetcher) throws LDAPException {
    Schema schema = fetcher.fetch(config);
    schemaCache.put(config.getName(), schema);
    logger.info("Refreshed schema cache for {}", config.getName());
    return schema;
  }

  void invalidate(String serverName) {
    schemaCache.invalidate(serverName);
  }

  void clearSchemaCache(String serverName) {
    invalidate(serverName);
    logger.debug("Cleared schema cache for {}", serverName);
  }

  void clearAllSchemaCaches() {
    schemaCache.invalidateAll();
    logger.debug("Cleared all schema caches");
  }

  Schema getIfPresent(String serverName) {
    return schemaCache.getIfPresent(serverName);
  }

  void put(String serverName, Schema schema) {
    schemaCache.put(serverName, schema);
  }
}

package com.ldapbrowser.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.RootDSE;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * Coordinates browse-related caches and per-key population locks.
 */
final class LdapBrowseCacheManager {

  @FunctionalInterface
  interface RootDseLoader {
    RootDSE load(LdapServerConfig config) throws LDAPException, GeneralSecurityException;
  }

  @FunctionalInterface
  interface NamingContextLoader {
    List<String> load(LdapServerConfig config) throws LDAPException, GeneralSecurityException;
  }

  @FunctionalInterface
  interface EntryLoader {
    LdapEntry load(LdapServerConfig config, String dn) throws LDAPException;
  }

  private final Cache<String, RootDSE> rootDseCache = boundedCache();
  private final Cache<String, List<String>> namingContextsCache = boundedCache();
  private final Cache<String, List<String>> privateNamingContextsCache = boundedCache();
  private final Cache<String, LdapEntry> entryMinimalCache = Caffeine.newBuilder()
      .maximumSize(1000)
      .expireAfterAccess(Duration.ofHours(1))
      .build();

  private final Map<String, Object> rootDseLocks = new ConcurrentHashMap<>();
  private final Map<String, Object> namingContextsLocks = new ConcurrentHashMap<>();
  private final Map<String, Object> privateNamingContextsLocks = new ConcurrentHashMap<>();
  private final Map<String, Object> entryMinimalLocks = new ConcurrentHashMap<>();

  private final Logger logger;

  LdapBrowseCacheManager(Logger logger) {
    this.logger = logger;
  }

  RootDSE getRootDse(LdapServerConfig config, RootDseLoader loader)
      throws LDAPException, GeneralSecurityException {
    return getWithLock(
        config.getName(),
        rootDseCache,
        rootDseLocks,
        () -> loader.load(config),
        value -> logger.debug("Cached Root DSE for {}", config.getName()),
        value -> logger.debug("Using cached Root DSE for {}", config.getName()),
        value -> logger.debug("Using cached Root DSE for {} (after sync)", config.getName()));
  }

  List<String> getNamingContexts(LdapServerConfig config, NamingContextLoader loader)
      throws LDAPException, GeneralSecurityException {
    return getWithLock(
        config.getName(),
        namingContextsCache,
        namingContextsLocks,
        () -> {
          List<String> contexts = loader.load(config);
          return contexts != null ? contexts : new ArrayList<>();
        },
        value -> logger.debug("Cached naming contexts for {}: {} contexts",
            config.getName(), value.size()),
        value -> logger.debug("Using cached naming contexts for {}", config.getName()),
        value -> logger.debug("Using cached naming contexts for {} (after sync)",
            config.getName()));
  }

  List<String> getPrivateNamingContexts(LdapServerConfig config, NamingContextLoader loader)
      throws LDAPException, GeneralSecurityException {
    return getWithLock(
        config.getName(),
        privateNamingContextsCache,
        privateNamingContextsLocks,
        () -> {
          List<String> contexts = loader.load(config);
          return contexts != null ? contexts : new ArrayList<>();
        },
        value -> logger.debug("Cached private naming contexts for {}: {} contexts",
            config.getName(), value.size()),
        value -> logger.debug("Using cached private naming contexts for {}",
            config.getName()),
        value -> logger.debug("Using cached private naming contexts for {} (after sync)",
            config.getName()));
  }

  LdapEntry getEntryMinimal(LdapServerConfig config, String dn, EntryLoader loader)
      throws LDAPException {
    String cacheKey = config.getName() + ":" + dn;
    LdapEntry entry = entryMinimalCache.getIfPresent(cacheKey);
    if (entry != null) {
      logger.debug("Using cached minimal entry for {}: {}", config.getName(), dn);
      return entry;
    }

    Object lock = entryMinimalLocks.computeIfAbsent(cacheKey, key -> new Object());
    synchronized (lock) {
      entry = entryMinimalCache.getIfPresent(cacheKey);
      if (entry != null) {
        logger.debug("Using cached minimal entry for {}: {} (after sync)",
            config.getName(), dn);
        return entry;
      }

      entry = loader.load(config, dn);
      if (entry != null) {
        entryMinimalCache.put(cacheKey, entry);
        logger.debug("Cached minimal entry for {}: {}", config.getName(), dn);
      }
      return entry;
    }
  }

  void clearBrowseCache(String serverName) {
    rootDseCache.invalidate(serverName);
    namingContextsCache.invalidate(serverName);
    privateNamingContextsCache.invalidate(serverName);
    rootDseLocks.remove(serverName);
    namingContextsLocks.remove(serverName);
    privateNamingContextsLocks.remove(serverName);
    entryMinimalCache.asMap().entrySet().removeIf(
        entry -> entry.getKey().startsWith(serverName + ":"));
    entryMinimalLocks.entrySet().removeIf(entry -> entry.getKey().startsWith(serverName + ":"));
    logger.debug("Cleared browse caches for {}", serverName);
  }

  void clearAllBrowseCaches() {
    rootDseCache.invalidateAll();
    namingContextsCache.invalidateAll();
    privateNamingContextsCache.invalidateAll();
    rootDseLocks.clear();
    namingContextsLocks.clear();
    privateNamingContextsLocks.clear();
    entryMinimalCache.invalidateAll();
    entryMinimalLocks.clear();
    logger.debug("Cleared all browse caches");
  }

  private static <T> T getWithLock(String key,
      Cache<String, T> cache,
      Map<String, Object> locks,
      Loader<T> loader,
      Consumer<T> onCachePut,
      Consumer<T> onCacheHit,
      Consumer<T> onCacheHitAfterSync)
      throws LDAPException, GeneralSecurityException {
    T value = cache.getIfPresent(key);
    if (value != null) {
      onCacheHit.accept(value);
      return value;
    }

    Object lock = locks.computeIfAbsent(key, unused -> new Object());
    synchronized (lock) {
      value = cache.getIfPresent(key);
      if (value != null) {
        onCacheHitAfterSync.accept(value);
        return value;
      }

      value = loader.load();
      if (value != null) {
        cache.put(key, value);
        onCachePut.accept(value);
      }
      return value;
    }
  }

  @FunctionalInterface
  private interface Loader<T> {
    T load() throws LDAPException, GeneralSecurityException;
  }

  private static <T> Cache<String, T> boundedCache() {
    return Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterAccess(Duration.ofHours(1))
        .build();
  }
}
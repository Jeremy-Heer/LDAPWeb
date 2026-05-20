package com.ldapbrowser.service;

import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Stores LDAP connection pools and their associated server configurations.
 */
final class LdapConnectionPoolStore {

  private final Map<String, LDAPConnectionPool> connectionPools = new ConcurrentHashMap<>();
  private final Map<String, LdapServerConfig> serverConfigs = new ConcurrentHashMap<>();

  Map<String, LDAPConnectionPool> connectionPools() {
    return connectionPools;
  }

  Map<String, LdapServerConfig> serverConfigs() {
    return serverConfigs;
  }

  boolean hasPool(String serverName) {
    return connectionPools.containsKey(serverName);
  }

  LDAPConnectionPool getPool(String serverName) {
    return connectionPools.get(serverName);
  }

  LDAPConnectionPool putPool(String serverName, LDAPConnectionPool pool) {
    return connectionPools.put(serverName, pool);
  }

  LDAPConnectionPool removePool(String serverName) {
    return connectionPools.remove(serverName);
  }

  LDAPConnectionPool computeIfAbsent(String serverName,
      Function<String, LDAPConnectionPool> creator) {
    return connectionPools.computeIfAbsent(serverName, creator);
  }

  void forEachPool(BiConsumer<String, LDAPConnectionPool> consumer) {
    connectionPools.forEach(consumer);
  }

  void clearPools() {
    connectionPools.clear();
  }

  void rememberConfig(String serverName, LdapServerConfig config) {
    serverConfigs.put(serverName, config);
  }

  LdapServerConfig getConfig(String serverName) {
    return serverConfigs.get(serverName);
  }

  void removeConfig(String serverName) {
    serverConfigs.remove(serverName);
  }
}
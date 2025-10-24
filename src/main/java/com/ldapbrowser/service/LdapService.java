package com.ldapbrowser.service;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.StartTLSPostConnectProcessor;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for LDAP operations.
 * Handles connection management, authentication, and LDAP operations.
 */
@Service
public class LdapService {

  private static final Logger logger = LoggerFactory.getLogger(LdapService.class);
  private static final int INITIAL_POOL_SIZE = 1;
  private static final int MAX_POOL_SIZE = 10;

  private final Map<String, LDAPConnectionPool> connectionPools = new ConcurrentHashMap<>();

  /**
   * Tests connection to an LDAP server.
   *
   * @param config server configuration
   * @return true if connection successful
   */
  public boolean testConnection(LdapServerConfig config) {
    LDAPConnection connection = null;
    try {
      connection = createConnection(config);

      // Test bind if credentials provided
      if (config.getBindDn() != null && !config.getBindDn().isEmpty()) {
        connection.bind(config.getBindDn(), config.getBindPassword());
        logger.info("Successfully bound to {} as {}", config.getName(), config.getBindDn());
      }

      logger.info("Connection test successful for {}", config.getName());
      return true;
    } catch (LDAPException e) {
      logger.error("Connection test failed for {}: {}", config.getName(), e.getMessage());
      return false;
    } catch (GeneralSecurityException e) {
      logger.error("SSL/TLS configuration error for {}: {}", config.getName(), e.getMessage());
      return false;
    } finally {
      if (connection != null) {
        connection.close();
      }
    }
  }

  /**
   * Creates a connection to the LDAP server.
   *
   * @param config server configuration
   * @return LDAP connection
   * @throws LDAPException if connection fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  private LDAPConnection createConnection(LdapServerConfig config)
      throws LDAPException, GeneralSecurityException {
    SocketFactory socketFactory = null;

    // Setup SSL if needed
    if (config.isUseSsl()) {
      SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
      SSLContext sslContext = sslUtil.createSSLContext();
      socketFactory = sslContext.getSocketFactory();
    }

    LDAPConnection connection;
    if (socketFactory != null) {
      connection = new LDAPConnection(socketFactory, config.getHost(), config.getPort());
    } else {
      connection = new LDAPConnection(config.getHost(), config.getPort());
    }

    // Setup StartTLS if needed
    if (config.isUseStartTls() && !config.isUseSsl()) {
      SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
      SSLContext sslContext = sslUtil.createSSLContext();
      connection.processExtendedOperation(
          new com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest(sslContext)
      );
    }

    return connection;
  }

  /**
   * Gets or creates a connection pool for the server.
   *
   * @param config server configuration
   * @return connection pool
   * @throws LDAPException if pool creation fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public LDAPConnectionPool getConnectionPool(LdapServerConfig config)
      throws LDAPException, GeneralSecurityException {
    String key = config.getName();

    return connectionPools.computeIfAbsent(key, k -> {
      try {
        LDAPConnection connection = createConnection(config);

        // Bind if credentials provided
        if (config.getBindDn() != null && !config.getBindDn().isEmpty()) {
          connection.bind(config.getBindDn(), config.getBindPassword());
        }

        StartTLSPostConnectProcessor postConnectProcessor = null;
        if (config.isUseStartTls() && !config.isUseSsl()) {
          SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
          SSLContext sslContext = sslUtil.createSSLContext();
          postConnectProcessor = new StartTLSPostConnectProcessor(sslContext);
        }

        LDAPConnectionPool pool = new LDAPConnectionPool(
            connection,
            INITIAL_POOL_SIZE,
            MAX_POOL_SIZE,
            postConnectProcessor
        );

        logger.info("Created connection pool for {}", config.getName());
        return pool;
      } catch (LDAPException | GeneralSecurityException e) {
        logger.error("Failed to create connection pool for {}", config.getName(), e);
        throw new RuntimeException("Failed to create connection pool", e);
      }
    });
  }

  /**
   * Closes connection pool for a server.
   *
   * @param serverName server name
   */
  public void closeConnectionPool(String serverName) {
    LDAPConnectionPool pool = connectionPools.remove(serverName);
    if (pool != null) {
      pool.close();
      logger.info("Closed connection pool for {}", serverName);
    }
  }

  /**
   * Closes all connection pools.
   */
  public void closeAllConnectionPools() {
    connectionPools.forEach((name, pool) -> {
      pool.close();
      logger.info("Closed connection pool for {}", name);
    });
    connectionPools.clear();
  }

  /**
   * Gets connection error message for display.
   *
   * @param e LDAP exception
   * @return user-friendly error message
   */
  public String getConnectionErrorMessage(LDAPException e) {
    return switch (e.getResultCode().intValue()) {
      case 49 -> "Invalid credentials - check bind DN and password";
      case 81 -> "Server is unavailable";
      case 91 -> "Connection timeout - check host and port";
      default -> "Connection failed: " + e.getMessage();
    };
  }

  /**
   * Performs LDAP search.
   *
   * @param config server configuration
   * @param baseDn base DN for search
   * @param filter LDAP filter
   * @param scope search scope
   * @return list of LDAP entries
   * @throws LDAPException if search fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public List<LdapEntry> search(
      LdapServerConfig config,
      String baseDn,
      String filter,
      SearchScope scope
  ) throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = getConnectionPool(config);
    SearchRequest searchRequest = new SearchRequest(baseDn, scope, filter);
    SearchResult searchResult = pool.search(searchRequest);

    List<LdapEntry> entries = new ArrayList<>();
    for (SearchResultEntry entry : searchResult.getSearchEntries()) {
      LdapEntry ldapEntry = new LdapEntry(entry.getDN(), config.getName());
      
      // Add regular attributes
      for (Attribute attr : entry.getAttributes()) {
        for (String value : attr.getValues()) {
          ldapEntry.addAttribute(attr.getName(), value);
        }
      }
      
      entries.add(ldapEntry);
    }

    logger.info("Search on {} returned {} entries", config.getName(), entries.size());
    return entries;
  }

  /**
   * Reads an LDAP entry with all attributes.
   *
   * @param config server configuration
   * @param dn distinguished name
   * @param includeOperational include operational attributes
   * @return LDAP entry
   * @throws LDAPException if read fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public LdapEntry readEntry(
      LdapServerConfig config,
      String dn,
      boolean includeOperational
  ) throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = getConnectionPool(config);
    
    SearchRequest searchRequest = new SearchRequest(
        dn,
        SearchScope.BASE,
        "(objectClass=*)",
        includeOperational ? new String[]{"*", "+"} : new String[]{"*"}
    );
    
    SearchResult searchResult = pool.search(searchRequest);
    if (searchResult.getEntryCount() == 0) {
      throw new LDAPException(
          com.unboundid.ldap.sdk.ResultCode.NO_SUCH_OBJECT,
          "Entry not found: " + dn
      );
    }

    SearchResultEntry entry = searchResult.getSearchEntries().get(0);
    LdapEntry ldapEntry = new LdapEntry(entry.getDN(), config.getName());

    // Add regular attributes
    for (Attribute attr : entry.getAttributes()) {
      boolean isOperational = attr.getName().startsWith("create")
          || attr.getName().startsWith("modify")
          || attr.getName().equals("entryUUID")
          || attr.getName().equals("entryDN");
      
      for (String value : attr.getValues()) {
        if (isOperational && includeOperational) {
          ldapEntry.addOperationalAttribute(attr.getName(), value);
        } else if (!isOperational) {
          ldapEntry.addAttribute(attr.getName(), value);
        }
      }
    }

    return ldapEntry;
  }

  /**
   * Modifies an LDAP entry attribute.
   *
   * @param config server configuration
   * @param dn distinguished name
   * @param attributeName attribute name
   * @param values new values
   * @throws LDAPException if modification fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public void modifyAttribute(
      LdapServerConfig config,
      String dn,
      String attributeName,
      List<String> values
  ) throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = getConnectionPool(config);
    
    Modification mod = new Modification(
        ModificationType.REPLACE,
        attributeName,
        values.toArray(new String[0])
    );
    
    pool.modify(dn, mod);
    logger.info("Modified attribute {} on {}", attributeName, dn);
  }

  /**
   * Adds attribute to entry.
   *
   * @param config server configuration
   * @param dn distinguished name
   * @param attributeName attribute name
   * @param values values to add
   * @throws LDAPException if add fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public void addAttribute(
      LdapServerConfig config,
      String dn,
      String attributeName,
      List<String> values
  ) throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = getConnectionPool(config);
    
    Modification mod = new Modification(
        ModificationType.ADD,
        attributeName,
        values.toArray(new String[0])
    );
    
    pool.modify(dn, mod);
    logger.info("Added attribute {} to {}", attributeName, dn);
  }

  /**
   * Deletes attribute from entry.
   *
   * @param config server configuration
   * @param dn distinguished name
   * @param attributeName attribute name
   * @throws LDAPException if delete fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public void deleteAttribute(
      LdapServerConfig config,
      String dn,
      String attributeName
  ) throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = getConnectionPool(config);
    
    Modification mod = new Modification(ModificationType.DELETE, attributeName);
    
    pool.modify(dn, mod);
    logger.info("Deleted attribute {} from {}", attributeName, dn);
  }

  /**
   * Deletes an LDAP entry.
   *
   * @param config server configuration
   * @param dn distinguished name
   * @throws LDAPException if delete fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public void deleteEntry(
      LdapServerConfig config,
      String dn
  ) throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = getConnectionPool(config);
    pool.delete(dn);
    logger.info("Deleted entry {}", dn);
  }

  /**
   * Tests bind with credentials.
   *
   * @param config server configuration
   * @param dn distinguished name
   * @param password password
   * @return true if bind successful
   */
  public boolean testBind(LdapServerConfig config, String dn, String password) {
    LDAPConnection connection = null;
    try {
      connection = createConnection(config);
      connection.bind(dn, password);
      logger.info("Bind test successful for {}", dn);
      return true;
    } catch (LDAPException | GeneralSecurityException e) {
      logger.error("Bind test failed for {}: {}", dn, e.getMessage());
      return false;
    } finally {
      if (connection != null) {
        connection.close();
      }
    }
  }
}

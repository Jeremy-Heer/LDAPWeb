package com.ldapbrowser.service;

import com.ldapbrowser.exception.CertificateValidationException;
import com.ldapbrowser.model.BrowseResult;
import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ModifyDNRequest;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.RootDSE;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.StartTLSPostConnectProcessor;
import com.unboundid.ldap.sdk.schema.Schema;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PreDestroy;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
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
  private static final int PAGE_SIZE = 100;
  
  // Connection pool health check settings
  private static final long HEALTH_CHECK_INTERVAL_MS = 60000; // 1 minute
  private static final long MAX_CONNECTION_AGE_MS = 300000; // 5 minutes

  private final LdapConnectionPoolStore connectionPoolStore = new LdapConnectionPoolStore();
  
  // Schema cache management - stores schema per server for performance
    private final LdapSchemaCacheManager schemaCacheManager = new LdapSchemaCacheManager(logger);
  private final LdapConnectionPoolManager connectionPoolManager = new LdapConnectionPoolManager(
      connectionPoolStore,
      schemaCacheManager,
      logger,
      this::createConnectionPool,
      this::fetchSchemaFromServer,
      this::checkServerAccess);
  
  // Paging state management for LDAP paged search control (thread-safe for multi-server usage)
  private final LdapPagingStateManager pagingStateManager = new LdapPagingStateManager();
  private final LdapPagingIterationHelper pagingIterationHelper =
      new LdapPagingIterationHelper();
    private final LdapBrowseRequestFactory browseRequestFactory =
      new LdapBrowseRequestFactory();
    private final LdapBrowseSearchExecutor browseSearchExecutor =
      new LdapBrowseSearchExecutor();
  
  // Root DSE and naming context caching to reduce redundant LDAP searches
  private final LdapBrowseCacheManager browseCacheManager =
      new LdapBrowseCacheManager(logger);
  private final LdapRootDseHelper rootDseHelper = new LdapRootDseHelper();
  private final LdapSchemaQueryHelper schemaQueryHelper = new LdapSchemaQueryHelper(logger);
  private final LdapSchemaModificationHelper schemaModificationHelper =
      new LdapSchemaModificationHelper(logger);
    private final LdapEffectiveRightsSearchHelper effectiveRightsSearchHelper =
      new LdapEffectiveRightsSearchHelper();
  private final LdapModifyRequestHelper modifyRequestHelper =
      new LdapModifyRequestHelper();
  private final LdapEntryMapper ldapEntryMapper = new LdapEntryMapper();
    private final LdapBrowsePageResultMapper browsePageResultMapper =
      new LdapBrowsePageResultMapper();
      private final LdapCertificateFailureHelper certificateFailureHelper =
        new LdapCertificateFailureHelper();
      private final LdapConnectionBindHelper connectionBindHelper =
        new LdapConnectionBindHelper();
        private final LdapConnectionBootstrapHelper connectionBootstrapHelper =
          new LdapConnectionBootstrapHelper();
        private final LdapConnectionCloseHelper connectionCloseHelper =
          new LdapConnectionCloseHelper();
  
  private final TruststoreService truststoreService;
  private final RoleService roleService;
  private final String authMode;
  private final LdapAttributeModificationHelper attributeModificationHelper;
  
  // Track the last trust manager used for certificate validation
  private final Map<String, TrustStoreTrustManager> trustManagers = new ConcurrentHashMap<>();
  private final LdapOperationExecutor ldapOperationExecutor = new LdapOperationExecutor();

  /**
   * Constructor with dependency injection.
   *
   * @param truststoreService service for managing trusted certificates
   * @param loggingService service for activity logging
   * @param roleService service for role-based access control
   * @param authMode authentication mode (none, local, oauth)
   */
  public LdapService(TruststoreService truststoreService, LoggingService loggingService,
      @Lazy RoleService roleService,
      @Value("${ldapbrowser.auth.mode:none}") String authMode) {
    this.truststoreService = truststoreService;
    this.roleService = roleService;
    this.authMode = authMode;
    this.attributeModificationHelper =
        new LdapAttributeModificationHelper(loggingService, logger);
  }

  /**
   * Tests connection to an LDAP server.
   *
   * @param config server configuration
   * @return true if connection successful
   * @throws com.ldapbrowser.exception.CertificateValidationException 
   *     if certificate validation fails
   */
  public boolean testConnection(LdapServerConfig config) 
      throws com.ldapbrowser.exception.CertificateValidationException {
    LDAPConnection connection = null;
    try {
      connection = createConnection(config);

      connectionBindHelper.bindIfConfigured(connection, config, logger);

      logger.info("Connection test successful for {}", config.getName());
      return true;
    } catch (LDAPException e) {
      CertificateValidationException certificateException =
          certificateFailureHelper.createCertificateValidationException(
              "Server certificate validation failed: ",
              e,
              e.getCause(),
              getLastFailedCertificate(config.getName()));
      if (certificateException != null) {
        logger.error("Certificate validation failed for {}", config.getName());
        throw certificateException;
      }
      
      logger.error("Connection test failed for {}: {}", config.getName(), e.getMessage());
      return false;
    } catch (GeneralSecurityException e) {
      logger.error("SSL/TLS configuration error for {}: {}", config.getName(), e.getMessage());
      return false;
    } finally {
      connectionCloseHelper.closeQuietly(connection);
    }
  }

  /**
   * Creates a connection to the LDAP server.
   *
   * @param config server configuration
   * @return LDAP connection
   * @throws LDAPException if connection fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   * @throws CertificateValidationException if certificate validation fails
   */
  private LDAPConnection createConnection(LdapServerConfig config)
      throws LDAPException, GeneralSecurityException, CertificateValidationException {
    SocketFactory socketFactory = null;

    // Setup SSL if needed
    if (config.isUseSsl()) {
      socketFactory = connectionBootstrapHelper.createSocketFactory(createSslUtil(config));
    }

    LDAPConnection connection;
    try {
      if (socketFactory != null) {
        connection = new LDAPConnection(socketFactory, config.getHost(), config.getPort());
      } else {
        connection = new LDAPConnection(config.getHost(), config.getPort());
      }
    } catch (LDAPException e) {
      // Check if this is a certificate validation failure
      checkForCertificateFailure(e, config);
      throw e; // Re-throw if not a certificate failure
    }

    // Setup StartTLS if needed
    if (config.isUseStartTls() && !config.isUseSsl()) {
      try {
        connectionBootstrapHelper.applyStartTlsIfNeeded(connection, config, createSslUtil(config));
      } catch (LDAPException e) {
        // Check if this is a certificate validation failure
        checkForCertificateFailure(e, config);
        throw e; // Re-throw if not a certificate failure
      }
    }

    return connection;
  }
  
  /**
   * Checks if an LDAPException is caused by certificate validation failure.
   * If so, retrieves the failed certificate and throws CertificateValidationException.
   *
   * @param e the LDAP exception to check
   * @param config server configuration
   * @throws CertificateValidationException if certificate validation failed
   */
  private void checkForCertificateFailure(LDAPException e, LdapServerConfig config)
      throws CertificateValidationException {
    CertificateValidationException certificateException =
        certificateFailureHelper.createCertificateValidationException(
            "Certificate validation failed: ",
            e,
            e,
            getLastFailedCertificate(config.getName()));
    if (certificateException != null) {
      throw certificateException;
    }
  }

  /**
   * Creates an SSLUtil instance with appropriate trust manager based on configuration.
   *
   * @param config server configuration
   * @return configured SSLUtil
   * @throws GeneralSecurityException if SSL setup fails
   */
  private SSLUtil createSslUtil(LdapServerConfig config) throws GeneralSecurityException {
    if (config.isValidateCertificate()) {
      // Use truststore for certificate validation
      try {
        Path truststorePath = truststoreService.getTruststorePath();
        char[] truststorePassword = truststoreService.getTruststorePassword();
        TrustStoreTrustManager trustManager = 
            new TrustStoreTrustManager(truststorePath, truststorePassword);
        
        // Store the trust manager for this server so we can retrieve failed certs
        trustManagers.put(config.getName(), trustManager);
        
        logger.debug("Using truststore validation for {}", config.getName());
        return new SSLUtil(trustManager);
      } catch (IOException e) {
        logger.error("Failed to load truststore for {}: {}", config.getName(), e.getMessage());
        throw new GeneralSecurityException(
            "Truststore could not be loaded for server "
                + config.getName() + ": " + e.getMessage(), e);
      }
    } else {
      // Trust all certificates when validation is disabled.
      logger.warn("Certificate validation is DISABLED for server '{}' — "
          + "all certificates will be trusted", config.getName());
      return new SSLUtil(new TrustAllTrustManager());
    }
  }

  /**
   * Gets the last failed certificate for a server.
   *
   * @param serverName the server name
   * @return the server certificate that failed validation, or null
   */
  public java.security.cert.X509Certificate getLastFailedCertificate(String serverName) {
    TrustStoreTrustManager trustManager = trustManagers.get(serverName);
    if (trustManager != null && trustManager.getLastFailedChain() != null 
        && trustManager.getLastFailedChain().length > 0) {
      return trustManager.getLastFailedChain()[0];
    }
    return null;
  }

  /**
   * Clears the stored certificate failure info for a server.
   *
   * @param serverName the server name
   */
  public void clearCertificateFailure(String serverName) {
    TrustStoreTrustManager trustManager = trustManagers.get(serverName);
    if (trustManager != null) {
      trustManager.clearFailureInfo();
    }
  }

  /**
   * Retrieves the server certificate by connecting to the server.
   * Uses a trust-all approach to ensure the certificate chain can be retrieved
   * even if it's not yet trusted. The full chain is needed for proper PKI validation.
   *
   * @param config server configuration
   * @return the server's X.509 certificate chain
   * @throws Exception if certificate chain cannot be retrieved
   */
  public X509Certificate[] retrieveServerCertificateChain(LdapServerConfig config) throws Exception {
    // Create a custom trust manager that captures the certificate chain
    CertificateCapturingTrustManager capturingTrustManager = 
        new CertificateCapturingTrustManager();
    SSLUtil sslUtil = new SSLUtil(capturingTrustManager);
    SSLContext sslContext = sslUtil.createSSLContext();
    
    LDAPConnection connection = null;
    try {
      if (config.isUseSsl()) {
        // Direct SSL connection
        SocketFactory socketFactory = sslContext.getSocketFactory();
        connection = new LDAPConnection(socketFactory, config.getHost(), config.getPort());
      } else if (config.isUseStartTls()) {
        // StartTLS connection
        connection = new LDAPConnection(config.getHost(), config.getPort());
        connection.processExtendedOperation(
            new com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest(sslContext)
        );
      } else {
        throw new IllegalArgumentException(
            "Server must use SSL or StartTLS to retrieve certificate");
      }
      
      // Return the captured certificate chain
      X509Certificate[] chain = capturingTrustManager.getCapturedChain();
      if (chain != null && chain.length > 0) {
        return chain;
      } else {
        throw new Exception("Failed to capture server certificate chain");
      }
    } finally {
      connectionCloseHelper.closeQuietly(connection);
    }
  }

  /**
   * Retrieves the SSL/TLS certificate from an LDAP server (first in chain).
   * Uses a trust-all approach to ensure the certificate can be retrieved
   * even if it's not yet trusted.
   *
   * @param config server configuration
   * @return the server's X.509 certificate
   * @throws Exception if certificate cannot be retrieved
   * @deprecated Use {@link #retrieveServerCertificateChain(LdapServerConfig)} for proper PKI validation
   */
  @Deprecated
  public X509Certificate retrieveServerCertificate(LdapServerConfig config) throws Exception {
    X509Certificate[] chain = retrieveServerCertificateChain(config);
    return (chain != null && chain.length > 0) ? chain[0] : null;
  }

  /**
   * Checks whether the current user has access to the given server.
   * When auth is disabled the check is skipped.
   *
   * @param serverName the server name to check
   * @throws SecurityException if the user is not allowed
   */
  private void checkServerAccess(String serverName) {
    if ("none".equalsIgnoreCase(authMode)) {
      return;
    }
    String username = getCurrentUsername();
    if (username == null || username.isEmpty()) {
      return; // no principal – allow (pre-auth requests like testConnection)
    }
    Set<String> allowed = roleService.getAllowedServersForUser(username);
    if (!allowed.contains(serverName)) {
      logger.warn("User {} denied access to server {}", username, serverName);
      throw new SecurityException(
          "Access denied: you do not have permission to access server '"
              + serverName + "'");
    }
  }

  /**
   * Returns the current authenticated username or null.
   */
  private String getCurrentUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return null;
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof UserDetails ud) {
      return ud.getUsername();
    }
    if (principal instanceof OidcUser oidc) {
      return oidc.getPreferredUsername() != null
          ? oidc.getPreferredUsername() : oidc.getEmail();
    }
    if ("anonymousUser".equals(principal.toString())) {
      return null;
    }
    return principal.toString();
  }

  /**
   * Gets or creates a connection pool for the server.
   * When a new connection pool is created, the schema is automatically fetched and cached.
   * Includes health check validation to detect and recover from stale connections.
   *
   * @param config server configuration
   * @return connection pool
   * @throws LDAPException if pool creation fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public LDAPConnectionPool getConnectionPool(LdapServerConfig config)
      throws LDAPException, GeneralSecurityException {
    return connectionPoolManager.getConnectionPoolForOperation(config, true);
  }

  /**
   * Gets or creates a connection pool for LDAP operations.
   *
   * @param config server configuration
   * @param validatePoolHealth whether to perform an explicit pool health probe
   * @return connection pool
   * @throws LDAPException if pool creation fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  LDAPConnectionPool getConnectionPoolForOperation(
      LdapServerConfig config,
      boolean validatePoolHealth
  ) throws LDAPException, GeneralSecurityException {
    return connectionPoolManager.getConnectionPoolForOperation(config, validatePoolHealth);
  }
  
  /**
   * Creates a new connection pool with health check configuration.
   *
   * @param config server configuration
   * @return configured connection pool
   * @throws LDAPException if pool creation fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   * @throws CertificateValidationException if certificate validation fails
   */
  private LDAPConnectionPool createConnectionPool(LdapServerConfig config)
      throws LDAPException, GeneralSecurityException, CertificateValidationException {
    LDAPConnection connection = createConnection(config);

    // Bind if credentials provided
    if (config.getBindDn() != null && !config.getBindDn().isEmpty()) {
      connection.bind(config.getBindDn(), config.getBindPassword());
    }

    StartTLSPostConnectProcessor postConnectProcessor = null;
    if (config.isUseStartTls() && !config.isUseSsl()) {
      SSLUtil sslUtil = createSslUtil(config);
      SSLContext sslContext = sslUtil.createSSLContext();
      postConnectProcessor = new StartTLSPostConnectProcessor(sslContext);
    }

    LDAPConnectionPool newPool = new LDAPConnectionPool(
        connection,
        INITIAL_POOL_SIZE,
        MAX_POOL_SIZE,
        postConnectProcessor
    );
    
    // Configure health checks to detect and replace stale connections.
    newPool.setHealthCheck(LdapConnectionHealthSupport.createPoolHealthCheck());
    
    // Set health check interval and connection age limits
    newPool.setHealthCheckIntervalMillis(HEALTH_CHECK_INTERVAL_MS);
    newPool.setMaxConnectionAgeMillis(MAX_CONNECTION_AGE_MS);
    
    // Enable background health checks
    newPool.setCreateIfNecessary(true);
    newPool.setCheckConnectionAgeOnRelease(true);
    
    logger.info("Created connection pool with health checks for {}", config.getName());
    return newPool;
  }
  
  /**
   * Executes an LDAP operation with automatic retry on stale connection errors.
   * If the operation fails due to connection issues, the pool is recreated and retried once.
   *
   * @param config server configuration
   * @param operation the LDAP operation to execute
   * @param <T> return type of the operation
   * @return result of the operation
   * @throws LDAPException if operation fails after retry
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  private <T> T executeWithRetry(LdapServerConfig config,
      LdapOperation<T> operation) throws LDAPException, GeneralSecurityException {
    return ldapOperationExecutor.executeWithRetry(
        config,
        () -> getConnectionPool(config),
        operation::execute,
        () -> recreateConnectionPool(config));
  }

  private boolean isConnectionRelatedError(ResultCode resultCode) {
    return ldapOperationExecutor.isConnectionRelatedError(resultCode);
  }

  /**
   * Recreates a server connection pool and returns a fresh instance.
   *
   * @param config server configuration
   * @return newly created connection pool
   * @throws LDAPException if pool creation fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  LDAPConnectionPool recreateConnectionPool(LdapServerConfig config)
      throws LDAPException, GeneralSecurityException {
    return connectionPoolManager.recreateConnectionPool(config);
  }
  
  /**
   * Functional interface for LDAP operations that can be retried.
   *
   * @param <T> return type of the operation
   */
  @FunctionalInterface
  private interface LdapOperation<T> {
    T execute(LDAPConnectionPool pool) throws LDAPException;
  }
  
  /**
   * Finds server config by name.
   *
   * @param serverName server name
   * @return server config or null if not found
   */
  private LdapServerConfig findConfigByName(String serverName) {
    return connectionPoolStore.getConfig(serverName);
  }

  /**
   * Closes connection pool for a server.
   *
   * @param serverName server name
   */
  public void closeConnectionPool(String serverName) {
    LDAPConnectionPool pool = connectionPoolStore.removePool(serverName);
    if (pool != null) {
      pool.close();
      logger.info("Closed connection pool for {}", serverName);
    }
    // Clear all caches for this server
    connectionPoolStore.removeConfig(serverName);
    clearSchemaCache(serverName);
    clearBrowseCache(serverName);
  }

  /**
   * Closes all connection pools.
   */
  @PreDestroy
  public void closeAllConnectionPools() {
    connectionPoolStore.forEachPool((name, pool) -> {
      pool.close();
      logger.info("Closed connection pool for {}", name);
    });
    connectionPoolStore.clearPools();
    // Clear all caches
    clearAllSchemaCaches();
    clearAllBrowseCaches();
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
    return search(config, baseDn, filter, scope, (String[]) null);
  }

  /**
   * Searches for LDAP entries with specified attributes.
   * Uses automatic retry logic to recover from stale connections.
   *
   * @param config server configuration
   * @param baseDn base DN for search
   * @param filter LDAP filter
   * @param scope search scope
   * @param attributes specific attributes to return (null for all user attributes)
   * @return list of LDAP entries
   * @throws LDAPException if search fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public List<LdapEntry> search(
      LdapServerConfig config,
      String baseDn,
      String filter,
      SearchScope scope,
      String... attributes
  ) throws LDAPException, GeneralSecurityException {
    Filter validatedFilter = toValidatedFilter(filter);
    return executeWithRetry(config, pool -> {
      SearchRequest searchRequest;
      if (attributes != null && attributes.length > 0) {
        searchRequest = new SearchRequest(baseDn, scope, validatedFilter, attributes);
      } else {
        searchRequest = new SearchRequest(baseDn, scope, validatedFilter);
      }
      SearchResult searchResult = pool.search(searchRequest);
      List<LdapEntry> entries =
          ldapEntryMapper.mapEntries(searchResult.getSearchEntries(), config.getName());

      logger.info("Search on {} returned {} entries", config.getName(), entries.size());
      return entries;
    });
  }

  /**
   * Searches for LDAP entries with specified attributes, size limit, and time limit.
   *
   * @param config server configuration
   * @param baseDn base DN for search
   * @param filter LDAP filter
   * @param scope search scope
   * @param sizeLimit maximum entries to return (0 for no limit)
   * @param timeLimitSeconds maximum seconds for search (0 for no limit)
   * @param attributes specific attributes to return (null for all user attributes)
   * @return list of LDAP entries
   * @throws LDAPException if search fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public List<LdapEntry> search(
      LdapServerConfig config,
      String baseDn,
      String filter,
      SearchScope scope,
      int sizeLimit,
      int timeLimitSeconds,
      String... attributes
  ) throws LDAPException, GeneralSecurityException {
    Filter validatedFilter = toValidatedFilter(filter);
    return executeWithRetry(config, pool -> {
      SearchRequest searchRequest;
      if (attributes != null && attributes.length > 0) {
        searchRequest = new SearchRequest(baseDn, scope, validatedFilter, attributes);
      } else {
        searchRequest = new SearchRequest(baseDn, scope, validatedFilter);
      }
      if (sizeLimit > 0) {
        searchRequest.setSizeLimit(sizeLimit);
      }
      if (timeLimitSeconds > 0) {
        searchRequest.setTimeLimitSeconds(timeLimitSeconds);
      }
      SearchResult searchResult;
      try {
        searchResult = pool.search(searchRequest);
      } catch (LDAPSearchException e) {
        if (e.getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED
            || e.getResultCode() == ResultCode.TIME_LIMIT_EXCEEDED) {
          searchResult = e.getSearchResult();
          if (searchResult == null) {
            return new ArrayList<>();
          }
        } else {
          throw e;
        }
      }

      List<LdapEntry> entries =
          ldapEntryMapper.mapEntries(searchResult.getSearchEntries(), config.getName());

      logger.info("Search on {} returned {} entries", config.getName(), entries.size());
      return entries;
    });
  }

  /**
   * Reads an LDAP entry with all attributes.
   * Uses automatic retry logic to recover from stale connections.
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
    return executeWithRetry(config, pool -> {
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

      // Get schema to properly classify attributes
      Schema schema = null;
      try {
        schema = getSchema(config);
      } catch (LDAPException e) {
        logger.warn("Failed to retrieve schema for operational attribute detection: {}", 
            e.getMessage());
      }

      return ldapEntryMapper.mapReadEntry(entry, config.getName(), includeOperational, schema);
    });
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
    attributeModificationHelper.modifyAttribute(
      pool,
        config.getName(),
        dn,
      attributeName,
      values
    );
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
    attributeModificationHelper.addAttribute(
      pool,
        config.getName(),
        dn,
      attributeName,
      values
    );
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
    attributeModificationHelper.deleteAttribute(
      pool,
        config.getName(),
        dn,
      attributeName
    );
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
   * Renames or moves an LDAP entry using a Modify DN operation.
   *
   * @param config server configuration
   * @param currentDn the current distinguished name of the entry
   * @param newRdn the new relative distinguished name
   * @param newParentDn the new parent DN, or null to keep current parent
   * @param deleteOldRdn whether to delete the old RDN value
   * @throws LDAPException if the modify DN operation fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public void modifyDN(
      LdapServerConfig config,
      String currentDn,
      String newRdn,
      String newParentDn,
      boolean deleteOldRdn
  ) throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = getConnectionPool(config);

    ModifyDNRequest request = new ModifyDNRequest(
        currentDn, newRdn, deleteOldRdn, newParentDn);
    pool.modifyDN(request);

    String newDn = newParentDn != null
        ? newRdn + "," + newParentDn
        : newRdn + "," + currentDn.substring(currentDn.indexOf(',') + 1);
    logger.info("Modified DN from {} to {}", currentDn, newDn);
  }

  /**
   * Adds a new LDAP entry.
   *
   * @param config server configuration
   * @param entry the entry to add
   * @throws LDAPException if add fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public void addEntry(LdapServerConfig config, LdapEntry entry)
      throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = getConnectionPool(config);
    
    // Build list of attributes from the entry
    List<com.unboundid.ldap.sdk.Attribute> attrs = new ArrayList<>();
    for (Map.Entry<String, List<String>> attrEntry : entry.getAttributes().entrySet()) {
      attrs.add(new com.unboundid.ldap.sdk.Attribute(
          attrEntry.getKey(),
          attrEntry.getValue().toArray(new String[0])
      ));
    }
    
    pool.add(entry.getDn(), attrs);
    logger.info("Added entry {}", entry.getDn());
  }

  /**
   * Modifies an LDAP entry with optional controls.
   *
   * @param config server configuration
   * @param dn distinguished name
   * @param modifications list of modifications
   * @param controls optional LDAP controls
   * @throws LDAPException if modify fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public void modifyEntry(LdapServerConfig config, String dn,
      List<com.unboundid.ldap.sdk.Modification> modifications, Control... controls)
      throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = getConnectionPool(config);
    modifyRequestHelper.applyModify(pool, dn, modifications, controls);
    
    logger.info("Modified entry {} with {} modifications", dn, modifications.size());
  }

  /**
   * Modifies an LDAP entry for bulk operations.
   *
   * <p>This path skips explicit pre-operation pool health probing to avoid a root DSE
   * read before every change. If a connection-level error occurs, the pool is recreated
   * and the modify is retried once.
   *
   * @param config server configuration
   * @param dn distinguished name
   * @param modifications list of modifications
   * @param controls optional LDAP controls
   * @throws LDAPException if modify fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public void modifyEntryBulkFast(LdapServerConfig config, String dn,
      List<com.unboundid.ldap.sdk.Modification> modifications, Control... controls)
      throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = getConnectionPoolForOperation(config, false);

    try {
      modifyRequestHelper.applyModify(pool, dn, modifications, controls);

      logger.info("Bulk-modified entry {} with {} modifications", dn, modifications.size());
    } catch (LDAPException e) {
      if (!isConnectionRelatedError(e.getResultCode())) {
        throw e;
      }

      logger.warn("Bulk modify failed with {}, retrying once for {}",
          e.getResultCode(), dn);

      LDAPConnectionPool freshPool = recreateConnectionPool(config);
      modifyRequestHelper.applyModify(freshPool, dn, modifications, controls);

      logger.info("Bulk-modified entry {} after retry", dn);
    }
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
    } catch (CertificateValidationException e) {
      logger.error("Certificate validation failed for bind test: {}", e.getMessage());
      return false;
    } catch (LDAPException | GeneralSecurityException e) {
      logger.error("Bind test failed for {}: {}", dn, e.getMessage());
      return false;
    } finally {
      connectionCloseHelper.closeQuietly(connection);
    }
  }
  
  /**
   * Browses entries under a given DN with pagination support.
   *
   * @param config server configuration
   * @param baseDn base DN
   * @return browse result with entries and pagination info
   * @throws LDAPException if browse fails
   */
  public BrowseResult browseEntries(LdapServerConfig config, String baseDn)
      throws LDAPException {
    return browseEntriesWithPage(config, baseDn, 0);
  }

  /**
   * Browses entries with specific page number using LDAP paged results control.
   * Uses automatic retry logic to recover from stale connections.
   *
   * @param config server configuration
   * @param baseDn base DN
   * @param pageNumber page number (0-based)
   * @return browse result with entries and pagination info
   * @throws LDAPException if browse fails
   */
  public BrowseResult browseEntriesWithPage(LdapServerConfig config, String baseDn, int pageNumber)
      throws LDAPException {
    return browseEntriesWithPage(config, baseDn, pageNumber, null);
  }

  /**
   * Browses entries with specific page number and custom filter using LDAP paged results control.
   * Uses automatic retry logic to recover from stale connections.
   *
   * @param config server configuration
   * @param baseDn base DN
   * @param pageNumber page number (0-based)
   * @param searchFilter custom LDAP search filter (null or empty for default)
   * @return browse result with entries and pagination info
   * @throws LDAPException if browse fails
   */
  public BrowseResult browseEntriesWithPage(LdapServerConfig config, String baseDn, 
      int pageNumber, String searchFilter) throws LDAPException {
    Filter validatedFilter = toValidatedFilter(searchFilter);
    try {
      return executeWithRetry(config, 
          pool -> browsePage(config, baseDn, pageNumber, pool, validatedFilter));
    } catch (GeneralSecurityException e) {
      throw new LDAPException(
          ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage()
      );
    }
  }
  
  /**
   * Internal method to browse a page of entries.
   *
   * @param config server configuration
   * @param baseDn base DN
   * @param pageNumber page number
   * @param pool connection pool to use
   * @param searchFilter custom LDAP search filter (null or empty for default)
   * @return browse result
   * @throws LDAPException if browse fails
   */
    private BrowseResult browsePage(LdapServerConfig config, String baseDn, 
      int pageNumber, LDAPConnectionPool pool, Filter searchFilter)
      throws LDAPException {
    
    // Create a unique key for this search context
    String searchKey = LdapPagingStateManager.buildSearchKey(config.getName(), baseDn);
    
    LdapPagingStateManager.PagingState pagingState =
        pagingStateManager.preparePageRequest(searchKey, pageNumber);
    if (pagingState.requiresIteration()) {
      // We don't have the right cookie position - need to iterate from beginning.
      // This happens when jumping to arbitrary pages or after a refresh.
      return browseEntriesWithPagingIteration(config, baseDn, pageNumber,
          searchFilter != null ? searchFilter.toString() : null);
    }
    byte[] cookie = pagingState.cookie();
    
    // Use custom filter if provided, otherwise default
    Filter filter = searchFilter != null ? searchFilter : toValidatedFilter(null);
    
    SearchRequest searchRequest =
        browseRequestFactory.createPagedBrowseRequest(baseDn, filter, PAGE_SIZE, cookie);

    return browseSearchExecutor.executePageSearch(
      pool,
      searchRequest,
      config.getName(),
      searchKey,
      pageNumber,
      PAGE_SIZE,
      baseDn,
      this::shouldShowExpanderForEntry,
      browsePageResultMapper,
      pagingStateManager);
  }

  /**
   * Parses and validates an LDAP filter using the UnboundID SDK.
   *
   * @param filter filter string, or null/empty for the default objectClass filter
   * @return validated filter
   * @throws LDAPException if the filter syntax is invalid
   */
  private Filter toValidatedFilter(String filter) throws LDAPException {
    if (filter == null || filter.isEmpty()) {
      return Filter.create("(objectClass=*)");
    }
    return Filter.create(filter);
  }
  
  /**
   * Helper method to iterate through pages when jumping to a specific page.
   * This is necessary because LDAP paged search doesn't support jumping to arbitrary pages.
   *
   * @param config server configuration
   * @param baseDn base DN
   * @param targetPage target page number
   * @param searchFilter custom LDAP search filter (null or empty for default)
   * @return browse result for target page
   * @throws LDAPException if operation fails
   */
  private BrowseResult browseEntriesWithPagingIteration(LdapServerConfig config, 
                                                         String baseDn, int targetPage,
                                                         String searchFilter)
      throws LDAPException {
    // Clear any existing state and start from page 0
    String searchKey = LdapPagingStateManager.buildSearchKey(config.getName(), baseDn);
    pagingStateManager.clearSearchContext(searchKey);

    return pagingIterationHelper.fetchTargetPage(
        targetPage,
        PAGE_SIZE,
        currentPage -> browseEntriesWithPage(config, baseDn, currentPage, searchFilter));
  }
  
  /**
   * Clear all paging cookies for a specific server.
   *
   * @param serverId server ID
   */
  public void clearPagingState(String serverId) {
    pagingStateManager.clearServer(serverId);
  }
  
  /**
   * Clear paging cookies for a specific search context.
   *
   * @param serverId server ID
   * @param baseDn base DN
   */
  public void clearPagingState(String serverId, String baseDn) {
    String searchKey = LdapPagingStateManager.buildSearchKey(serverId, baseDn);
    pagingStateManager.clearSearchContext(searchKey);
  }
  
  /**
   * Gets naming contexts from Root DSE.
   * Uses automatic retry logic to recover from stale connections.
   * Results are cached to reduce redundant LDAP searches.
   *
   * @param config server configuration
   * @return list of naming contexts
   * @throws LDAPException if operation fails
   */
  public List<String> getNamingContexts(LdapServerConfig config) throws LDAPException {
    try {
      return browseCacheManager.getNamingContexts(config, this::loadNamingContexts);
    } catch (GeneralSecurityException e) {
      throw new LDAPException(
          com.unboundid.ldap.sdk.ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage()
      );
    }
  }

  /**
   * Gets private naming contexts from Root DSE.
   * Results are cached to reduce redundant LDAP searches.
   *
   * @param config server configuration
   * @return list of private naming contexts
   * @throws LDAPException if operation fails
   */
  public List<String> getPrivateNamingContexts(LdapServerConfig config) throws LDAPException {
    try {
      return browseCacheManager.getPrivateNamingContexts(config, this::loadPrivateNamingContexts);
    } catch (GeneralSecurityException e) {
      throw new LDAPException(
          com.unboundid.ldap.sdk.ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage()
      );
    }
  }
  
  /**
   * Gets entry with minimal attributes.
   * Results are cached to reduce redundant LDAP searches.
   *
   * @param config server configuration
   * @param dn distinguished name
   * @return LDAP entry
   * @throws LDAPException if operation fails
   */
  public LdapEntry getEntryMinimal(LdapServerConfig config, String dn) throws LDAPException {
    return browseCacheManager.getEntryMinimal(config, dn, this::loadMinimalEntry);
  }
  
  /**
   * Clears paging state for a server.
   *
   * @param config server configuration
   */
  public void clearPagingState(LdapServerConfig config) {
    clearPagingState(config.getName());
  }
  
  private boolean shouldShowExpanderForEntry(LdapEntry entry) {
    List<String> objectClasses = entry.getAttributeValues("objectClass");
    if (objectClasses == null || objectClasses.isEmpty()) {
      return false;
    }
    
    for (String oc : objectClasses) {
      String lowerOc = oc.toLowerCase();
      // Leaf entries
      if (lowerOc.contains("person") || lowerOc.contains("user")
          || lowerOc.contains("inetorgperson") || lowerOc.contains("computer")
          || lowerOc.contains("device") || lowerOc.contains("printer")) {
        return false;
      }
    }
    
    return true;
  }
  
  /**
   * Retrieves the schema from an LDAP server with caching.
   * This method uses the Extended Schema Info control when supported to retrieve
   * additional schema metadata such as X-SCHEMA-FILE.
   * The schema is cached per server for performance.
   *
   * @param config server configuration
   * @return schema object
   * @throws LDAPException if schema retrieval fails
   */
  public Schema getSchema(LdapServerConfig config) throws LDAPException {
    return schemaCacheManager.getSchema(config, this::fetchSchemaFromServer);
  }
  
  /**
   * Fetches the schema directly from the LDAP server without using cache.
   * This method uses the Extended Schema Info control when supported.
   * Uses automatic retry logic to recover from stale connections.
   *
   * @param config server configuration
   * @return schema object
   * @throws LDAPException if schema retrieval fails
   */
  private Schema fetchSchemaFromServer(LdapServerConfig config) throws LDAPException {
    try {
      return executeWithRetry(config, pool -> fetchSchemaInternal(config, pool));
    } catch (GeneralSecurityException e) {
      throw new LDAPException(
          ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage()
      );
    }
  }
  
  /**
   * Internal method to fetch schema from a connection pool.
   *
   * @param config server configuration
   * @param pool connection pool
   * @return schema object
   * @throws LDAPException if schema retrieval fails
   */
  private Schema fetchSchemaInternal(LdapServerConfig config, LDAPConnectionPool pool)
      throws LDAPException {
      
      // Try to retrieve schema with Extended Schema Info control for Ping Directory
      try {
        String schemaDN = getSchemaSubentryDN(config);
        Schema schema = schemaQueryHelper.tryRetrieveWithExtendedControl(pool, schemaDN);
        if (schema != null) {
          logger.info("Retrieved schema with extended info from {}", config.getName());
          return schema;
        }
      } catch (LDAPException e) {
        // Fall back to standard retrieval
        logger.debug("Extended schema retrieval failed, using standard method", e);
      }
      
      // Standard schema retrieval (fallback)
      Schema schema = pool.getSchema();
      logger.info("Retrieved schema from {}", config.getName());
      return schema;
  }
  
  /**
   * Refreshes the cached schema for a server by re-fetching from LDAP.
   *
   * @param config server configuration
   * @return refreshed schema object
   * @throws LDAPException if schema retrieval fails
   */
  public Schema refreshSchema(LdapServerConfig config) throws LDAPException {
    return schemaCacheManager.refreshSchema(config, this::fetchSchemaFromServer);
  }
  
  /**
   * Clears the cached schema for a specific server.
   *
   * @param serverName server name
   */
  public void clearSchemaCache(String serverName) {
    schemaCacheManager.clearSchemaCache(serverName);
  }
  
  /**
   * Clears all cached schemas.
   */
  public void clearAllSchemaCaches() {
    schemaCacheManager.clearAllSchemaCaches();
  }
  
  /**
   * Clears browse-related caches (Root DSE, naming contexts, minimal entries) for a server.
   *
   * @param serverName server name
   */
  public void clearBrowseCache(String serverName) {
    browseCacheManager.clearBrowseCache(serverName);
  }
  
  /**
   * Clears all browse-related caches for all servers.
   */
  public void clearAllBrowseCaches() {
    browseCacheManager.clearAllBrowseCaches();
  }

  private List<String> loadNamingContexts(LdapServerConfig config)
      throws LDAPException, GeneralSecurityException {
    RootDSE rootDse = getRootDSE(config);
    return rootDseHelper.getNamingContexts(rootDse);
  }

  private List<String> loadPrivateNamingContexts(LdapServerConfig config)
      throws LDAPException, GeneralSecurityException {
    RootDSE rootDse = getRootDSE(config);
    return rootDseHelper.getPrivateNamingContexts(rootDse);
  }

  private LdapEntry loadMinimalEntry(LdapServerConfig config, String dn) throws LDAPException {
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      SearchRequest searchRequest = new SearchRequest(
          dn,
          SearchScope.BASE,
          "(objectClass=*)",
          "objectClass", "cn", "ou", "dc"
      );

      SearchResult searchResult = pool.search(searchRequest);
      if (searchResult.getEntryCount() == 0) {
        return null;
      }

      SearchResultEntry entry = searchResult.getSearchEntries().get(0);
      LdapEntry ldapEntry = ldapEntryMapper.mapEntry(entry, config.getName());

      ldapEntry.setHasChildren(shouldShowExpanderForEntry(ldapEntry));
      return ldapEntry;
    } catch (GeneralSecurityException e) {
      throw new LDAPException(
          com.unboundid.ldap.sdk.ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage()
      );
    }
  }

  /**
   * Gets a list of all attribute names from the schemas of the specified servers.
   * Combines attribute names from all selected servers and returns a sorted unique list.
   *
   * @param configs list of server configurations
   * @return sorted list of attribute names
   */
  public List<String> getAllAttributeNames(List<LdapServerConfig> configs) {
    java.util.Set<String> attributeNames = new java.util.TreeSet<>();
    
    for (LdapServerConfig config : configs) {
      try {
        Schema schema = getSchema(config);
        for (com.unboundid.ldap.sdk.schema.AttributeTypeDefinition attrType 
            : schema.getAttributeTypes()) {
          // Add the primary name
          attributeNames.add(attrType.getNameOrOID());
          // Add all alternative names
          for (String name : attrType.getNames()) {
            attributeNames.add(name);
          }
        }
      } catch (LDAPException e) {
        logger.warn("Failed to get schema for {}: {}", config.getName(), e.getMessage());
      }
    }
    
    return new ArrayList<>(attributeNames);
  }

  /**
   * Retrieves the schema from an LDAP server by server ID.
   *
   * @param serverId server ID (name)
   * @param useExtendedControl whether to use the Extended Schema Info control
   * @return schema object
   * @throws LDAPException if schema retrieval fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public Schema getSchema(String serverId, boolean useExtendedControl) 
      throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = connectionPoolStore.getPool(serverId);
    if (pool == null) {
      throw new LDAPException(ResultCode.CONNECT_ERROR, "Not connected to server: " + serverId);
    }

    if (useExtendedControl) {
      try {
        // Get root DSE to find schema DN (uses cache)
        LdapServerConfig config = findConfigByName(serverId);
        RootDSE rootDSE = config != null ? getRootDSE(config) : pool.getRootDSE();
        String schemaDN = rootDseHelper.getSchemaSubentryDn(rootDSE);
        Schema schema = schemaQueryHelper.tryRetrieveWithExtendedControl(pool, schemaDN);
        if (schema != null) {
          logger.debug("Retrieved schema with extended control from {}", serverId);
          return schema;
        }
      } catch (LDAPException e) {
        logger.debug("Extended schema retrieval failed for {}, using standard", serverId);
      }
    }
    
    // Standard retrieval
    Schema schema = pool.getSchema();
    logger.debug("Retrieved schema from {}", serverId);
    return schema;
  }
  
  /**
   * Checks if connected to a server.
   *
   * @param serverName server name
   * @return true if connected
   */
  public boolean isConnected(String serverName) {
    return connectionPoolStore.hasPool(serverName);
  }

  /**
   * Ensures connection pool exists for a server configuration.
   *
   * @param config server configuration
   * @throws LDAPException if connection fails
   */
  public void connect(LdapServerConfig config) throws LDAPException {
    try {
      getConnectionPool(config);
    } catch (GeneralSecurityException e) {
      throw new LDAPException(
          ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage()
      );
    }
  }

  /**
   * Gets the Root DSE (Directory Server Entry) from the LDAP server.
   * The Root DSE contains server-specific information like supported controls,
   * naming contexts, schema location, etc.
   * Uses automatic retry logic to recover from stale connections.
   * Results are cached to reduce redundant LDAP searches.
   *
   * @param config server configuration
   * @return RootDSE object or null if not available
   * @throws LDAPException if retrieval fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public RootDSE getRootDSE(LdapServerConfig config) 
      throws LDAPException, GeneralSecurityException {
    return browseCacheManager.getRootDse(config, this::loadRootDse);
  }

  private RootDSE loadRootDse(LdapServerConfig config)
      throws LDAPException, GeneralSecurityException {
    logger.debug("Fetching Root DSE for {}", config.getName());
    return executeWithRetry(config, LDAPConnectionPool::getRootDSE);
  }

  /**
   * Checks if the server supports a specific LDAP control.
   *
   * @param serverId server ID (name)
   * @param controlOid control OID to check
   * @return true if the control is supported
   * @throws LDAPException if checking fails
   */
  public boolean isControlSupported(String serverId, String controlOid) throws LDAPException {
    try {
      LDAPConnectionPool pool = connectionPoolStore.getPool(serverId);
      if (pool == null) {
        return false;
      }
      
      // Use cached getRootDSE() method
      LdapServerConfig config = findConfigByName(serverId);
      RootDSE rootDSE = config != null ? getRootDSE(config) : pool.getRootDSE();
      return rootDseHelper.isControlSupported(rootDSE, controlOid);
    } catch (Exception e) {
      logger.debug("Error checking control support for {}: {}", serverId, e.getMessage());
      return false;
    }
  }

  /**
   * Checks if the server supports a specific LDAP control.
   *
   * @param config server configuration
   * @param controlOid control OID to check
   * @return true if the control is supported
   * @throws LDAPException if checking fails
   */
  public boolean isControlSupported(LdapServerConfig config, String controlOid) 
      throws LDAPException {
    return isControlSupported(config.getName(), controlOid);
  }

  /**
   * Checks if the server supports schema modifications.
   *
   * @param config server configuration
   * @return true if schema modification is supported
   */
  public boolean supportsSchemaModification(LdapServerConfig config) {
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      // Use cached getRootDSE() method
      RootDSE rootDSE = getRootDSE(config);
      
      if (rootDSE != null) {
        if (rootDseHelper.supportsSchemaModification(rootDSE)) {
          return true;
        }
        
        // Check if schema subentry is accessible
        String schemaDN = getSchemaSubentryDN(config);
        return schemaModificationHelper.canAccessSchemaSubentry(pool, schemaDN);
      }
    } catch (Exception e) {
      logger.debug("Error checking schema modification support: {}", e.getMessage());
    }
    return false;
  }

  /**
   * Gets the schema subentry DN from root DSE.
   *
   * @param config server configuration
   * @return schema subentry DN or null if not found
   */
  private String getSchemaSubentryDN(LdapServerConfig config) {
    try {
      getConnectionPool(config);
      // Use cached getRootDSE() method
      RootDSE rootDSE = getRootDSE(config);
      return rootDseHelper.getSchemaSubentryDn(rootDSE);
    } catch (Exception e) {
      logger.debug("Error getting schema subentry DN: {}", e.getMessage());
      return "cn=schema";
    }
  }

  /**
   * Modifies an object class in the schema.
   *
   * @param config server configuration
   * @param oldDefinition old object class definition
   * @param newDefinition new object class definition
   * @throws LDAPException if modification fails
   */
  public void modifyObjectClassInSchema(LdapServerConfig config, String oldDefinition,
      String newDefinition) throws LDAPException {
    // PingDirectory handles modifications via ADD operation only; oldDefinition unused.
    applySchemaModification(config, "objectClasses", newDefinition,
        "Modified object class in schema on {}");
  }

  /**
   * Modifies an attribute type in the schema.
   *
   * @param config server configuration
   * @param oldDefinition old attribute type definition
   * @param newDefinition new attribute type definition
   * @throws LDAPException if modification fails
   */
  public void modifyAttributeTypeInSchema(LdapServerConfig config, String oldDefinition,
      String newDefinition) throws LDAPException {
    // PingDirectory handles modifications via ADD operation only; oldDefinition unused.
    applySchemaModification(config, "attributeTypes", newDefinition,
        "Modified attribute type in schema on {}");
  }

  /**
   * Adds an object class to the schema.
   *
   * @param config server configuration
   * @param definition object class definition
   * @throws LDAPException if add fails
   */
  public void addObjectClassToSchema(LdapServerConfig config, String definition)
      throws LDAPException {
    applySchemaModification(config, "objectClasses", definition,
        "Added object class to schema on {}");
  }

  /**
   * Adds an attribute type to the schema.
   *
   * @param config server configuration
   * @param definition attribute type definition
   * @throws LDAPException if add fails
   */
  public void addAttributeTypeToSchema(LdapServerConfig config, String definition)
      throws LDAPException {
    applySchemaModification(config, "attributeTypes", definition,
        "Added attribute type to schema on {}");
  }

  /**
   * Shared helper for all schema ADD modifications.
   *
   * <p>Retrieves the schema subentry DN, applies a single {@code ADD} modification
   * on the given schema attribute, and logs the result.
   *
   * @param config              server configuration
   * @param schemaAttributeName LDAP attribute holding the definition
   *                            ({@code objectClasses} or {@code attributeTypes})
   * @param definition          the new schema definition string
   * @param logMessage          SLF4J message template (single {@code {}} placeholder
   *                            for server name)
   * @throws LDAPException if the modification fails or the schema DN cannot
   *                       be determined
   */
  private void applySchemaModification(LdapServerConfig config, String schemaAttributeName,
      String definition, String logMessage) throws LDAPException {
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      String schemaDN = getSchemaSubentryDN(config);

      if (schemaDN == null) {
        throw new LDAPException(ResultCode.NO_SUCH_OBJECT,
            "Cannot determine schema subentry DN");
      }

      schemaModificationHelper.applyAddModification(
          pool,
          schemaDN,
          schemaAttributeName,
          definition
      );
      logger.info(logMessage, config.getName());
    } catch (GeneralSecurityException e) {
      throw new LDAPException(ResultCode.LOCAL_ERROR, "SSL/TLS error: " + e.getMessage());
    }
  }

  /**
   * Searches for entries with effective rights information using GetEffectiveRightsRequestControl.
   *
   * @param config server configuration
   * @param searchBase base DN for search
   * @param scope search scope
   * @param filter search filter
   * @param attributes attributes to return (or "*" for all)
   * @param effectiveRightsFor authorization identity (e.g., "dn: uid=user,dc=example,dc=com")
   * @param sizeLimit maximum number of entries to return (0 for no limit)
   * @return search result with entries and size limit exceeded flag
   * @throws LDAPException if search fails
   */
  public SearchResultWithEffectiveRights searchEffectiveRights(
      LdapServerConfig config,
      String searchBase,
      SearchScope scope,
      String filter,
      String attributes,
      String effectiveRightsFor,
      int sizeLimit) throws LDAPException {
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      return effectiveRightsSearchHelper.searchEffectiveRights(
          pool,
          searchBase,
          scope,
          filter,
          attributes,
          effectiveRightsFor,
          sizeLimit
      );
    } catch (GeneralSecurityException e) {
      throw new LDAPException(ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage());
    }
  }

  /**
   * Result holder for effective rights search with size limit information.
   */
  public static class SearchResultWithEffectiveRights {
    private final List<SearchResultEntry> entries;
    private final boolean sizeLimitExceeded;

    public SearchResultWithEffectiveRights(List<SearchResultEntry> entries, 
        boolean sizeLimitExceeded) {
      this.entries = entries;
      this.sizeLimitExceeded = sizeLimitExceeded;
    }

    public List<SearchResultEntry> getEntries() {
      return entries;
    }

    public boolean isSizeLimitExceeded() {
      return sizeLimitExceeded;
    }
  }
}

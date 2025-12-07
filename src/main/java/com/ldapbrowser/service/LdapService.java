package com.ldapbrowser.service;

import com.ldapbrowser.exception.CertificateValidationException;
import com.ldapbrowser.model.BrowseResult;
import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.RootDSE;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.StartTLSPostConnectProcessor;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
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
  private static final int PAGE_SIZE = 100;
  
  // Connection pool health check settings
  private static final long HEALTH_CHECK_INTERVAL_MS = 60000; // 1 minute
  private static final long MAX_CONNECTION_AGE_MS = 300000; // 5 minutes

  private final Map<String, LDAPConnectionPool> connectionPools = new ConcurrentHashMap<>();
  
  // Schema cache management - stores schema per server for performance
  private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();
  
  // Paging state management for LDAP paged search control (thread-safe for multi-server usage)
  private final Map<String, byte[]> pagingCookies = new ConcurrentHashMap<>();
  private final Map<String, Integer> currentPages = new ConcurrentHashMap<>();
  
  private final TruststoreService truststoreService;
  private final LoggingService loggingService;
  
  // Track the last trust manager used for certificate validation
  private final Map<String, TrustStoreTrustManager> trustManagers = new ConcurrentHashMap<>();

  /**
   * Constructor with dependency injection.
   *
   * @param truststoreService service for managing trusted certificates
   * @param loggingService service for activity logging
   */
  public LdapService(TruststoreService truststoreService, LoggingService loggingService) {
    this.truststoreService = truststoreService;
    this.loggingService = loggingService;
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

      // Test bind if credentials provided
      if (config.getBindDn() != null && !config.getBindDn().isEmpty()) {
        connection.bind(config.getBindDn(), config.getBindPassword());
        logger.info("Successfully bound to {} as {}", config.getName(), config.getBindDn());
      }

      logger.info("Connection test successful for {}", config.getName());
      return true;
    } catch (LDAPException e) {
      // Check if this was caused by a certificate validation failure
      Throwable cause = e.getCause();
      if (cause instanceof javax.net.ssl.SSLHandshakeException 
          || cause instanceof java.security.cert.CertificateException) {
        
        // Try to get the failed certificate from the trust manager
        java.security.cert.X509Certificate failedCert = getLastFailedCertificate(config.getName());
        
        if (failedCert != null) {
          logger.error("Certificate validation failed for {}", config.getName());
          throw new com.ldapbrowser.exception.CertificateValidationException(
              "Server certificate validation failed: " + cause.getMessage(),
              cause,
              new java.security.cert.X509Certificate[]{failedCert}
          );
        }
      }
      
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
   * @throws CertificateValidationException if certificate validation fails
   */
  private LDAPConnection createConnection(LdapServerConfig config)
      throws LDAPException, GeneralSecurityException, CertificateValidationException {
    SocketFactory socketFactory = null;

    // Setup SSL if needed
    if (config.isUseSsl()) {
      SSLUtil sslUtil = createSslUtil(config);
      SSLContext sslContext = sslUtil.createSSLContext();
      socketFactory = sslContext.getSocketFactory();
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
      SSLUtil sslUtil = createSslUtil(config);
      SSLContext sslContext = sslUtil.createSSLContext();
      try {
        connection.processExtendedOperation(
            new com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest(sslContext)
        );
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
    Throwable cause = e.getCause();
    while (cause != null) {
      if (cause instanceof javax.net.ssl.SSLHandshakeException
          || cause instanceof java.security.cert.CertificateException) {
        // Certificate validation failed - try to get the certificate
        X509Certificate cert = getLastFailedCertificate(config.getName());
        if (cert != null) {
          throw new CertificateValidationException(
              "Certificate validation failed: " + cause.getMessage(),
              e,
              new X509Certificate[]{cert}
          );
        }
        break;
      }
      cause = cause.getCause();
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
        logger.error("Failed to load truststore, falling back to trust all: {}", e.getMessage());
        // Fall back to trust all if truststore can't be loaded
        return new SSLUtil(new TrustAllTrustManager());
      }
    } else {
      // Trust all certificates when validation is disabled
      logger.debug("Certificate validation disabled for {}", config.getName());
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
   * Uses a trust-all approach to ensure the certificate can be retrieved
   * even if it's not yet trusted.
   *
   * @param config server configuration
   * @return the server's X.509 certificate
   * @throws Exception if certificate cannot be retrieved
   */
  public X509Certificate retrieveServerCertificate(LdapServerConfig config) throws Exception {
    // Create a custom trust manager that captures the certificate
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
      
      // Return the captured certificate
      X509Certificate cert = capturingTrustManager.getCapturedCertificate();
      if (cert != null) {
        return cert;
      } else {
        throw new Exception("Failed to capture server certificate");
      }
    } finally {
      if (connection != null) {
        connection.close();
      }
    }
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
    String key = config.getName();
    boolean isNewConnection = !connectionPools.containsKey(key);

    LDAPConnectionPool pool;
    try {
      pool = connectionPools.computeIfAbsent(key, k -> {
        try {
          return createConnectionPool(config);
        } catch (CertificateValidationException e) {
          // Wrap certificate validation exception so it can escape computeIfAbsent
          throw new RuntimeException("CERT_VALIDATION_FAILED", e);
        } catch (LDAPException | GeneralSecurityException e) {
          logger.error("Failed to create connection pool for {}", config.getName(), e);
          throw new RuntimeException("Failed to create connection pool", e);
        }
      });
    } catch (RuntimeException e) {
      // Certificate validation exceptions are wrapped - they'll be detected in UI
      throw e;
    }
    
    // Validate pool health and recreate if stale
    if (!isNewConnection && !isPoolHealthy(pool)) {
      logger.warn("Connection pool for {} is unhealthy, recreating", config.getName());
      pool.close();
      connectionPools.remove(key);
      schemaCache.remove(key);
      try {
        pool = createConnectionPool(config);
        connectionPools.put(key, pool);
        isNewConnection = true;
      } catch (CertificateValidationException e) {
        logger.error("Certificate validation failed for {}", config.getName());
        throw new RuntimeException("CERT_VALIDATION_FAILED", e);
      } catch (LDAPException | GeneralSecurityException e) {
        logger.error("Failed to recreate connection pool for {}", config.getName(), e);
        throw new RuntimeException("Failed to recreate connection pool", e);
      }
    }
    
    // If this is a new connection, fetch and cache the schema
    if (isNewConnection && !schemaCache.containsKey(key)) {
      try {
        Schema schema = fetchSchemaFromServer(config);
        schemaCache.put(key, schema);
        logger.debug("Pre-fetched and cached schema for {}", config.getName());
      } catch (LDAPException e) {
        // Log but don't fail - schema can be fetched later if needed
        logger.warn("Failed to pre-fetch schema for {}: {}", config.getName(), e.getMessage());
      }
    }
    
    return pool;
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
      SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
      SSLContext sslContext = sslUtil.createSSLContext();
      postConnectProcessor = new StartTLSPostConnectProcessor(sslContext);
    }

    LDAPConnectionPool newPool = new LDAPConnectionPool(
        connection,
        INITIAL_POOL_SIZE,
        MAX_POOL_SIZE,
        postConnectProcessor
    );
    
    // Configure health checks to detect and replace stale connections
    newPool.setHealthCheck(new com.unboundid.ldap.sdk.LDAPConnectionPoolHealthCheck() {
      @Override
      public void ensureNewConnectionValid(LDAPConnection connection) throws LDAPException {
        // Test connection with a simple operation
        connection.getRootDSE();
      }

      @Override
      public void ensureConnectionValidAfterException(LDAPConnection connection,
          LDAPException exception) throws LDAPException {
        // If we get certain exceptions, consider the connection invalid
        ResultCode resultCode = exception.getResultCode();
        if (resultCode == ResultCode.SERVER_DOWN
            || resultCode == ResultCode.CONNECT_ERROR
            || resultCode == ResultCode.UNAVAILABLE
            || resultCode == ResultCode.TIMEOUT
            || resultCode == ResultCode.DECODING_ERROR) {
          throw exception; // Connection is invalid, pool will replace it
        }
        // For other exceptions, test if connection is still valid
        connection.getRootDSE();
      }

      @Override
      public void ensureConnectionValidForCheckout(LDAPConnection connection)
          throws LDAPException {
        // Quick check before giving connection to caller
        if (!connection.isConnected()) {
          throw new LDAPException(ResultCode.SERVER_DOWN,
              "Connection is not established");
        }
      }

      @Override
      public void ensureConnectionValidForRelease(LDAPConnection connection)
          throws LDAPException {
        // Check when returning connection to pool
        if (!connection.isConnected()) {
          throw new LDAPException(ResultCode.SERVER_DOWN,
              "Connection is not established");
        }
      }

      @Override
      public void ensureConnectionValidForContinuedUse(LDAPConnection connection)
          throws LDAPException {
        // Periodic health check for idle connections
        if (!connection.isConnected()) {
          throw new LDAPException(ResultCode.SERVER_DOWN,
              "Connection is not established");
        }
      }
    });
    
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
   * Checks if a connection pool is healthy.
   *
   * @param pool connection pool to check
   * @return true if pool is healthy
   */
  private boolean isPoolHealthy(LDAPConnectionPool pool) {
    if (pool == null || pool.isClosed()) {
      return false;
    }
    
    try {
      // Try to get a connection and perform a simple operation
      LDAPConnection conn = pool.getConnection();
      try {
        conn.getRootDSE();
        pool.releaseConnection(conn);
        return true;
      } catch (LDAPException e) {
        logger.debug("Health check failed: {}", e.getMessage());
        pool.releaseDefunctConnection(conn);
        return false;
      }
    } catch (LDAPException e) {
      logger.debug("Failed to get connection for health check: {}", e.getMessage());
      return false;
    }
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
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      return operation.execute(pool);
    } catch (LDAPException e) {
      // Check if this is a connection-related error that warrants a retry
      ResultCode resultCode = e.getResultCode();
      if (resultCode == ResultCode.SERVER_DOWN
          || resultCode == ResultCode.CONNECT_ERROR
          || resultCode == ResultCode.UNAVAILABLE
          || resultCode == ResultCode.TIMEOUT
          || resultCode == ResultCode.DECODING_ERROR) {
        
        logger.warn("LDAP operation failed with {}, attempting retry after pool recreation",
            resultCode);
        
        // Force pool recreation
        String key = config.getName();
        LDAPConnectionPool oldPool = connectionPools.remove(key);
        if (oldPool != null) {
          oldPool.close();
        }
        schemaCache.remove(key);
        
        // Retry the operation with a fresh pool
        try {
          LDAPConnectionPool newPool = getConnectionPool(config);
          T result = operation.execute(newPool);
          logger.info("LDAP operation succeeded after retry for {}", config.getName());
          return result;
        } catch (LDAPException retryException) {
          logger.error("LDAP operation failed after retry for {}: {}",
              config.getName(), retryException.getMessage());
          throw retryException;
        }
      }
      
      // Not a connection error, rethrow immediately
      throw e;
    }
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
    // Also clear the schema cache for this server
    clearSchemaCache(serverName);
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
    // Also clear all schema caches
    clearAllSchemaCaches();
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
    return executeWithRetry(config, pool -> {
      SearchRequest searchRequest;
      if (attributes != null && attributes.length > 0) {
        searchRequest = new SearchRequest(baseDn, scope, filter, attributes);
      } else {
        searchRequest = new SearchRequest(baseDn, scope, filter);
      }
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
      LdapEntry ldapEntry = new LdapEntry(entry.getDN(), config.getName());

      // Get schema to properly classify attributes
      Schema schema = null;
      try {
        schema = getSchema(config);
      } catch (LDAPException e) {
        logger.warn("Failed to retrieve schema for operational attribute detection: {}", 
            e.getMessage());
      }

      // Add attributes, classifying them as operational or user attributes
      for (Attribute attr : entry.getAttributes()) {
        boolean isOperational = isOperationalAttribute(attr.getName(), schema);
        
        for (String value : attr.getValues()) {
          if (isOperational && includeOperational) {
            ldapEntry.addOperationalAttribute(attr.getName(), value);
          } else if (!isOperational) {
            ldapEntry.addAttribute(attr.getName(), value);
          }
        }
      }

      return ldapEntry;
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
    
    Modification mod = new Modification(
        ModificationType.REPLACE,
        attributeName,
        values.toArray(new String[0])
    );
    
    pool.modify(dn, mod);
    logger.info("Modified attribute {} on {}", attributeName, dn);
    
    // Generate LDIF format
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append("\n");
    ldif.append("changetype: modify\n");
    ldif.append("replace: ").append(attributeName).append("\n");
    for (String value : values) {
      ldif.append(attributeName).append(": ").append(value).append("\n");
    }
    ldif.append("-");
    
    // Log to activity log
    loggingService.logModification(
        config.getName(),
        "Modified attribute '" + attributeName + "' on entry",
        dn,
        ldif.toString()
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
    
    Modification mod = new Modification(
        ModificationType.ADD,
        attributeName,
        values.toArray(new String[0])
    );
    
    pool.modify(dn, mod);
    logger.info("Added attribute {} to {}", attributeName, dn);
    
    // Generate LDIF format
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append("\n");
    ldif.append("changetype: modify\n");
    ldif.append("add: ").append(attributeName).append("\n");
    for (String value : values) {
      ldif.append(attributeName).append(": ").append(value).append("\n");
    }
    ldif.append("-");
    
    // Log to activity log
    loggingService.logModification(
        config.getName(),
        "Added attribute '" + attributeName + "' to entry",
        dn,
        ldif.toString()
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
    
    Modification mod = new Modification(ModificationType.DELETE, attributeName);
    
    pool.modify(dn, mod);
    logger.info("Deleted attribute {} from {}", attributeName, dn);
    
    // Generate LDIF format
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append("\n");
    ldif.append("changetype: modify\n");
    ldif.append("delete: ").append(attributeName).append("\n");
    ldif.append("-");
    
    // Log to activity log
    loggingService.logModification(
        config.getName(),
        "Deleted attribute '" + attributeName + "' from entry",
        dn,
        ldif.toString()
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
    
    if (controls != null && controls.length > 0) {
      com.unboundid.ldap.sdk.ModifyRequest modifyRequest = 
          new com.unboundid.ldap.sdk.ModifyRequest(dn, modifications, controls);
      pool.modify(modifyRequest);
    } else {
      pool.modify(dn, modifications);
    }
    
    logger.info("Modified entry {} with {} modifications", dn, modifications.size());
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
      if (connection != null) {
        connection.close();
      }
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
    try {
      return executeWithRetry(config, 
          pool -> browsePage(config, baseDn, pageNumber, pool, searchFilter));
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
      int pageNumber, LDAPConnectionPool pool, String searchFilter)
      throws LDAPException {
    
    // Create a unique key for this search context
    String searchKey = config.getName() + ":" + baseDn;
    
    List<LdapEntry> entries = new ArrayList<>();
    boolean hasNextPage = false;
    
    // Get current stored page and cookie for this search context
    Integer storedPage = currentPages.get(searchKey);
    byte[] cookie = pagingCookies.get(searchKey);
    
    // Handle different navigation scenarios
    if (pageNumber == 0) {
      // First page - start fresh
      pagingCookies.remove(searchKey);
      currentPages.put(searchKey, 0);
      cookie = null;
    } else if (storedPage == null || storedPage != pageNumber - 1) {
      // We don't have the right cookie position - need to iterate from beginning
      // This happens when jumping to arbitrary pages or after a refresh
      return browseEntriesWithPagingIteration(config, baseDn, pageNumber, searchFilter);
    }
    // else: we have the right cookie for sequential navigation (page = storedPage + 1)
    
    // Use custom filter if provided, otherwise default
    String filter = (searchFilter != null && !searchFilter.isEmpty()) 
        ? searchFilter : "(objectClass=*)";
    
    // Create the paged search control
    SimplePagedResultsControl pagedControl;
    if (cookie != null) {
      pagedControl = new SimplePagedResultsControl(PAGE_SIZE, new ASN1OctetString(cookie), false);
    } else {
      pagedControl = new SimplePagedResultsControl(PAGE_SIZE, false);
    }
    
    // Create search request with essential attributes only
    SearchRequest searchRequest = new SearchRequest(
        baseDn,
        SearchScope.ONE,
        filter,
        "objectClass", "cn", "ou", "dc"
    );
    
    // Add the paged results control
    searchRequest.addControl(pagedControl);
    
    try {
      SearchResult searchResult = pool.search(searchRequest);
      
      // Extract entries from this page
      for (SearchResultEntry entry : searchResult.getSearchEntries()) {
        LdapEntry ldapEntry = new LdapEntry(entry.getDN(), config.getName());
        
        for (Attribute attr : entry.getAttributes()) {
          for (String value : attr.getValues()) {
            ldapEntry.addAttribute(attr.getName(), value);
          }
        }
        
        // Determine if entry likely has children
        ldapEntry.setHasChildren(shouldShowExpanderForEntry(ldapEntry));
        entries.add(ldapEntry);
      }
      
      // Sort entries by display name
      entries.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
      
      // Check for paged results response control to get the cookie for next page
      SimplePagedResultsControl responseControl = null;
      for (Control control : searchResult.getResponseControls()) {
        if (control instanceof SimplePagedResultsControl) {
          responseControl = (SimplePagedResultsControl) control;
          break;
        }
      }
      
      if (responseControl != null) {
        ASN1OctetString cookieOctetString = responseControl.getCookie();
        if (cookieOctetString != null && cookieOctetString.getValueLength() > 0) {
          // Store cookie for next page
          byte[] nextCookie = cookieOctetString.getValue();
          pagingCookies.put(searchKey, nextCookie);
          currentPages.put(searchKey, pageNumber);
          hasNextPage = true;
        } else {
          // No more pages
          pagingCookies.remove(searchKey);
          currentPages.remove(searchKey);
          hasNextPage = false;
        }
      }
      
      // Calculate pagination info
      boolean hasPrevPage = pageNumber > 0;
      
      return new BrowseResult(entries, hasNextPage, entries.size(), 
          pageNumber, PAGE_SIZE, hasNextPage, hasPrevPage);
      
    } catch (LDAPSearchException e) {
      // Handle SIZE_LIMIT_EXCEEDED gracefully
      if (e.getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED) {
        // Process partial results from the exception
        for (SearchResultEntry entry : e.getSearchEntries()) {
          LdapEntry ldapEntry = new LdapEntry(entry.getDN(), config.getName());
          
          for (Attribute attr : entry.getAttributes()) {
            for (String value : attr.getValues()) {
              ldapEntry.addAttribute(attr.getName(), value);
            }
          }
          
          ldapEntry.setHasChildren(shouldShowExpanderForEntry(ldapEntry));
          entries.add(ldapEntry);
        }
        
        entries.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
        
        logger.warn("Size limit exceeded for {}, returning {} partial results", 
            baseDn, entries.size());
        
        return new BrowseResult(entries, true, entries.size(), 
            pageNumber, PAGE_SIZE, true, pageNumber > 0);
      } else {
        // Re-throw other types of LDAP exceptions
        throw e;
      }
    }
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
    String searchKey = config.getName() + ":" + baseDn;
    pagingCookies.remove(searchKey);
    currentPages.remove(searchKey);
    
    // Iterate through pages until we reach the target page
    boolean hasMorePages = true;
    BrowseResult lastResult = null;
    
    for (int currentPage = 0; currentPage <= targetPage && hasMorePages; currentPage++) {
      lastResult = browseEntriesWithPage(config, baseDn, currentPage, searchFilter);
      
      if (currentPage == targetPage) {
        // This is our target page
        return lastResult;
      }
      
      hasMorePages = lastResult.hasNextPage();
    }
    
    // If we reach here, the target page doesn't exist
    return new BrowseResult(new ArrayList<>(), false, 0, targetPage, PAGE_SIZE, false, targetPage > 0);
  }
  
  /**
   * Clear all paging cookies for a specific server.
   *
   * @param serverId server ID
   */
  public void clearPagingState(String serverId) {
    pagingCookies.entrySet().removeIf(entry -> entry.getKey().startsWith(serverId + ":"));
    currentPages.entrySet().removeIf(entry -> entry.getKey().startsWith(serverId + ":"));
  }
  
  /**
   * Clear paging cookies for a specific search context.
   *
   * @param serverId server ID
   * @param baseDn base DN
   */
  public void clearPagingState(String serverId, String baseDn) {
    String searchKey = serverId + ":" + baseDn;
    pagingCookies.remove(searchKey);
    currentPages.remove(searchKey);
  }
  
  /**
   * Gets naming contexts from Root DSE.
   * Uses automatic retry logic to recover from stale connections.
   *
   * @param config server configuration
   * @return list of naming contexts
   * @throws LDAPException if operation fails
   */
  public List<String> getNamingContexts(LdapServerConfig config) throws LDAPException {
    try {
      return executeWithRetry(config, pool -> {
        SearchRequest searchRequest = new SearchRequest(
            "",
            SearchScope.BASE,
            "(objectClass=*)",
            "namingContexts"
        );
        
        SearchResult searchResult = pool.search(searchRequest);
        if (searchResult.getEntryCount() == 0) {
          return new ArrayList<>();
        }
        
        SearchResultEntry rootDse = searchResult.getSearchEntries().get(0);
        Attribute namingContextsAttr = rootDse.getAttribute("namingContexts");
        
        if (namingContextsAttr != null) {
          return List.of(namingContextsAttr.getValues());
        }
        
        return new ArrayList<>();
      });
    } catch (GeneralSecurityException e) {
      throw new LDAPException(
          com.unboundid.ldap.sdk.ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage()
      );
    }
  }

  /**
   * Gets private naming contexts from Root DSE.
   *
   * @param config server configuration
   * @return list of private naming contexts
   * @throws LDAPException if operation fails
   */
  public List<String> getPrivateNamingContexts(LdapServerConfig config) throws LDAPException {
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      SearchRequest searchRequest = new SearchRequest(
          "",
          SearchScope.BASE,
          "(objectClass=*)",
          "ds-private-naming-contexts"
      );
      
      SearchResult searchResult = pool.search(searchRequest);
      if (searchResult.getEntryCount() == 0) {
        return new ArrayList<>();
      }
      
      SearchResultEntry rootDse = searchResult.getSearchEntries().get(0);
      Attribute privateContextsAttr = rootDse.getAttribute("ds-private-naming-contexts");
      
      if (privateContextsAttr != null) {
        return List.of(privateContextsAttr.getValues());
      }
      
      return new ArrayList<>();
    } catch (GeneralSecurityException e) {
      throw new LDAPException(
          com.unboundid.ldap.sdk.ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage()
      );
    }
  }
  
  /**
   * Gets entry with minimal attributes.
   *
   * @param config server configuration
   * @param dn distinguished name
   * @return LDAP entry
   * @throws LDAPException if operation fails
   */
  public LdapEntry getEntryMinimal(LdapServerConfig config, String dn) throws LDAPException {
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
      LdapEntry ldapEntry = new LdapEntry(entry.getDN(), config.getName());
      
      for (Attribute attr : entry.getAttributes()) {
        for (String value : attr.getValues()) {
          ldapEntry.addAttribute(attr.getName(), value);
        }
      }
      
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
   * Clears paging state for a server.
   *
   * @param config server configuration
   */
  public void clearPagingState(LdapServerConfig config) {
    // Placeholder for paging support
    logger.debug("Clearing paging state for {}", config.getName());
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
    // Check cache first
    String cacheKey = config.getName();
    Schema cachedSchema = schemaCache.get(cacheKey);
    if (cachedSchema != null) {
      return cachedSchema;
    }
    
    // Not in cache, fetch from server
    Schema schema = fetchSchemaFromServer(config);
    
    // Cache the schema
    schemaCache.put(cacheKey, schema);
    
    return schema;
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
        if (schemaDN != null && !schemaDN.isEmpty()) {
          SearchRequest req = new SearchRequest(
              schemaDN,
              SearchScope.BASE,
              "(objectClass=*)",
              // Request all common schema attributes
              "attributeTypes", "objectClasses", "ldapSyntaxes", "matchingRules",
              "matchingRuleUse", "dITContentRules", "nameForms", "dITStructureRules"
          );
          
          // Add Extended Schema Info control (non-critical) to get X-SCHEMA-FILE
          // OID: 1.3.6.1.4.1.30221.2.5.12 - Extended Schema Info Request Control
          req.addControl(new Control("1.3.6.1.4.1.30221.2.5.12", false));
          
          SearchResult sr = pool.search(req);
          if (sr.getEntryCount() > 0) {
            SearchResultEntry entry = sr.getSearchEntries().get(0);
            try {
              // Build Schema directly from the schema subentry with extended info
              Schema schema = new Schema(entry);
              logger.info("Retrieved schema with extended info from {}", config.getName());
              return schema;
            } catch (Throwable t) {
              // Fall through to standard retrieval
              logger.debug("Could not parse schema from subentry, using standard retrieval", t);
            }
          }
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
    String cacheKey = config.getName();
    Schema schema = fetchSchemaFromServer(config);
    schemaCache.put(cacheKey, schema);
    logger.info("Refreshed schema cache for {}", config.getName());
    return schema;
  }
  
  /**
   * Clears the cached schema for a specific server.
   *
   * @param serverName server name
   */
  public void clearSchemaCache(String serverName) {
    schemaCache.remove(serverName);
    logger.debug("Cleared schema cache for {}", serverName);
  }
  
  /**
   * Clears all cached schemas.
   */
  public void clearAllSchemaCaches() {
    schemaCache.clear();
    logger.debug("Cleared all schema caches");
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
   */
  public Schema getSchema(String serverId, boolean useExtendedControl) throws LDAPException {
    LDAPConnectionPool pool = connectionPools.get(serverId);
    if (pool == null) {
      throw new LDAPException(ResultCode.CONNECT_ERROR, "Not connected to server: " + serverId);
    }

    if (useExtendedControl) {
      try {
        // Get root DSE to find schema DN
        RootDSE rootDSE = pool.getRootDSE();
        String schemaDN = rootDSE != null 
            ? rootDSE.getAttributeValue("subschemaSubentry") 
            : "cn=schema";
        
        if (schemaDN == null || schemaDN.isEmpty()) {
          schemaDN = "cn=schema";
        }

        SearchRequest req = new SearchRequest(
            schemaDN,
            SearchScope.BASE,
            "(objectClass=*)",
            "attributeTypes", "objectClasses", "ldapSyntaxes", "matchingRules",
            "matchingRuleUse", "dITContentRules", "nameForms", "dITStructureRules"
        );
        
        // Add Extended Schema Info control (non-critical) to get X-SCHEMA-FILE
        // OID: 1.3.6.1.4.1.30221.2.5.12 - Extended Schema Info Request Control
        req.addControl(new Control("1.3.6.1.4.1.30221.2.5.12", false));
        
        SearchResult sr = pool.search(req);
        if (sr.getEntryCount() > 0) {
          SearchResultEntry entry = sr.getSearchEntries().get(0);
          Schema schema = new Schema(entry);
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
    return connectionPools.containsKey(serverName);
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
   *
   * @param config server configuration
   * @return RootDSE object or null if not available
   * @throws LDAPException if retrieval fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public RootDSE getRootDSE(LdapServerConfig config) 
      throws LDAPException, GeneralSecurityException {
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
      LDAPConnectionPool pool = connectionPools.get(serverId);
      if (pool == null) {
        return false;
      }
      
      RootDSE rootDSE = pool.getRootDSE();
      if (rootDSE != null) {
        String[] supportedControls = rootDSE.getAttributeValues("supportedControl");
        if (supportedControls != null) {
          for (String control : supportedControls) {
            if (controlOid.equals(control)) {
              return true;
            }
          }
        }
      }
      return false;
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
      RootDSE rootDSE = pool.getRootDSE();
      
      if (rootDSE != null) {
        // Check for schema modification support indicators
        String[] supportedFeatures = rootDSE.getAttributeValues("supportedFeatures");
        if (supportedFeatures != null) {
          for (String feature : supportedFeatures) {
            // Check for various schema modification OIDs
            if ("1.3.6.1.4.1.4203.1.5.1".equals(feature) || // All Operational Attributes
                "1.3.6.1.4.1.42.2.27.9.5.4".equals(feature)) { // Sun DS Schema Modification
              return true;
            }
          }
        }
        
        // Check if schema subentry is accessible
        String schemaDN = getSchemaSubentryDN(config);
        if (schemaDN != null) {
          try {
            SearchResult result = pool.search(schemaDN, SearchScope.BASE, "(objectClass=*)", "objectClass");
            return result != null && result.getSearchEntries().size() > 0;
          } catch (LDAPException e) {
            logger.debug("Schema subentry not accessible: {}", e.getMessage());
          }
        }
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
      LDAPConnectionPool pool = getConnectionPool(config);
      RootDSE rootDSE = pool.getRootDSE();
      
      if (rootDSE != null) {
        // Try different attributes in order of preference
        String schemaDN = rootDSE.getAttributeValue("subschemaSubentry");
        if (schemaDN != null) {
          return schemaDN;
        }
        
        schemaDN = rootDSE.getAttributeValue("schemaNamingContext");
        if (schemaDN != null) {
          return schemaDN;
        }
        
        schemaDN = rootDSE.getAttributeValue("schemaSubentry");
        if (schemaDN != null) {
          return schemaDN;
        }
      }
      
      // Fallback to common default
      return "cn=schema";
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
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      String schemaDN = getSchemaSubentryDN(config);
      
      if (schemaDN == null) {
        throw new LDAPException(ResultCode.NO_SUCH_OBJECT,
            "Cannot determine schema subentry DN");
      }
      
      // PingDirectory handles modifications via ADD operation only
      // No need to delete the old definition first
      Modification addModification = new Modification(ModificationType.ADD,
          "objectClasses", newDefinition);
      
      pool.modify(schemaDN, addModification);
      
      logger.info("Modified object class in schema on {}", config.getName());
    } catch (GeneralSecurityException e) {
      throw new LDAPException(ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage());
    }
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
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      String schemaDN = getSchemaSubentryDN(config);
      
      if (schemaDN == null) {
        throw new LDAPException(ResultCode.NO_SUCH_OBJECT,
            "Cannot determine schema subentry DN");
      }
      
      // PingDirectory handles modifications via ADD operation only
      // No need to delete the old definition first
      Modification addModification = new Modification(ModificationType.ADD,
          "attributeTypes", newDefinition);
      
      pool.modify(schemaDN, addModification);
      
      logger.info("Modified attribute type in schema on {}", config.getName());
    } catch (GeneralSecurityException e) {
      throw new LDAPException(ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage());
    }
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
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      String schemaDN = getSchemaSubentryDN(config);
      
      if (schemaDN == null) {
        throw new LDAPException(ResultCode.NO_SUCH_OBJECT,
            "Cannot determine schema subentry DN");
      }
      
      Modification addModification = new Modification(ModificationType.ADD,
          "objectClasses", definition);
      
      pool.modify(schemaDN, addModification);
      
      logger.info("Added object class to schema on {}", config.getName());
    } catch (GeneralSecurityException e) {
      throw new LDAPException(ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage());
    }
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
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      String schemaDN = getSchemaSubentryDN(config);
      
      if (schemaDN == null) {
        throw new LDAPException(ResultCode.NO_SUCH_OBJECT,
            "Cannot determine schema subentry DN");
      }
      
      Modification addModification = new Modification(ModificationType.ADD,
          "attributeTypes", definition);
      
      pool.modify(schemaDN, addModification);
      
      logger.info("Added attribute type to schema on {}", config.getName());
    } catch (GeneralSecurityException e) {
      throw new LDAPException(ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage());
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
      
      // Parse attributes for the search
      String[] attributeArray;
      if ("*".equals(attributes.trim())) {
        attributeArray = new String[] { "*", "aclRights" };
      } else {
        List<String> attrList = new ArrayList<>();
        for (String attr : attributes.split(",")) {
          attrList.add(attr.trim());
        }
        attrList.add("aclRights");
        attributeArray = attrList.toArray(new String[0]);
      }

      // Create search request with GetEffectiveRightsRequestControl
      SearchRequest searchRequest = new SearchRequest(
          searchBase,
          scope,
          Filter.create(filter),
          attributeArray
      );
      
      searchRequest.setSizeLimit(sizeLimit);
      searchRequest.addControl(
          new com.unboundid.ldap.sdk.unboundidds.controls.GetEffectiveRightsRequestControl(
              effectiveRightsFor));
      
      // Execute the search
      SearchResult searchResult = pool.search(searchRequest);
      
      // Check if size limit was exceeded
      boolean sizeLimitExceeded = (searchResult.getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED)
          || (sizeLimit > 0 && searchResult.getEntryCount() >= sizeLimit);
      
      return new SearchResultWithEffectiveRights(searchResult.getSearchEntries(), sizeLimitExceeded);
      
    } catch (LDAPSearchException e) {
      // Handle size limit exceeded - return partial results
      if (e.getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED) {
        SearchResult partialResult = e.getSearchResult();
        if (partialResult != null) {
          return new SearchResultWithEffectiveRights(partialResult.getSearchEntries(), true);
        }
        // If we got SIZE_LIMIT_EXCEEDED but no partial result, return empty with flag
        return new SearchResultWithEffectiveRights(new ArrayList<>(), true);
      } else if (e.getResultCode() == ResultCode.UNAVAILABLE_CRITICAL_EXTENSION) {
        throw new LDAPException(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
            "Server does not support GetEffectiveRightsRequestControl", e);
      }
      throw e;
    } catch (GeneralSecurityException e) {
      throw new LDAPException(ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage());
    }
  }

  /**
   * Determines if an attribute is operational based on its schema definition.
   * An attribute is operational if its usage is directoryOperation, dSAOperation,
   * or distributedOperation (anything other than userApplications).
   *
   * @param attributeName the attribute name to check
   * @param schema the schema to use for lookup (may be null)
   * @return true if the attribute is operational according to schema
   */
  private boolean isOperationalAttribute(String attributeName, Schema schema) {
    if (schema == null) {
      return false;
    }
    
    AttributeTypeDefinition attrDef = schema.getAttributeType(attributeName);
    if (attrDef == null) {
      return false;
    }
    
    // Check usage - operational attributes have usage other than "userApplications"
    if (attrDef.getUsage() != null) {
      String usage = attrDef.getUsage().getName();
      // Operational attributes have usage: directoryOperation, dSAOperation, or distributedOperation
      return !usage.equalsIgnoreCase("userApplications");
    }
    
    return false;
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

package com.ldapbrowser.service;

import com.ldapbrowser.model.BrowseResult;
import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.util.OidLookupTable;
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
import com.unboundid.ldap.sdk.schema.Schema;
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
  private static final int PAGE_SIZE = 100;

  private final Map<String, LDAPConnectionPool> connectionPools = new ConcurrentHashMap<>();
  
  // Paging state management for LDAP paged search control (thread-safe for multi-server usage)
  private final Map<String, byte[]> pagingCookies = new ConcurrentHashMap<>();
  private final Map<String, Integer> currentPages = new ConcurrentHashMap<>();

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
    return search(config, baseDn, filter, scope, (String[]) null);
  }

  /**
   * Searches for LDAP entries with specified attributes.
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
    LDAPConnectionPool pool = getConnectionPool(config);
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
   *
   * @param config server configuration
   * @param baseDn base DN
   * @param pageNumber page number (0-based)
   * @return browse result with entries and pagination info
   * @throws LDAPException if browse fails
   */
  public BrowseResult browseEntriesWithPage(LdapServerConfig config, String baseDn, int pageNumber)
      throws LDAPException {
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      
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
        return browseEntriesWithPagingIteration(config, baseDn, pageNumber);
      }
      // else: we have the right cookie for sequential navigation (page = storedPage + 1)
      
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
          "(objectClass=*)",
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
      
    } catch (GeneralSecurityException e) {
      throw new LDAPException(
          ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage()
      );
    }
  }
  
  /**
   * Helper method to iterate through pages when jumping to a specific page.
   * This is necessary because LDAP paged search doesn't support jumping to arbitrary pages.
   *
   * @param config server configuration
   * @param baseDn base DN
   * @param targetPage target page number
   * @return browse result for target page
   * @throws LDAPException if operation fails
   */
  private BrowseResult browseEntriesWithPagingIteration(LdapServerConfig config, 
                                                         String baseDn, int targetPage)
      throws LDAPException {
    // Clear any existing state and start from page 0
    String searchKey = config.getName() + ":" + baseDn;
    pagingCookies.remove(searchKey);
    currentPages.remove(searchKey);
    
    // Iterate through pages until we reach the target page
    boolean hasMorePages = true;
    BrowseResult lastResult = null;
    
    for (int currentPage = 0; currentPage <= targetPage && hasMorePages; currentPage++) {
      lastResult = browseEntriesWithPage(config, baseDn, currentPage);
      
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
   *
   * @param config server configuration
   * @return list of naming contexts
   * @throws LDAPException if operation fails
   */
  public List<String> getNamingContexts(LdapServerConfig config) throws LDAPException {
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
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
   * Retrieves the schema from an LDAP server.
   * This method uses the Extended Schema Info control when supported to retrieve
   * additional schema metadata such as X-SCHEMA-FILE.
   *
   * @param config server configuration
   * @return schema object
   * @throws LDAPException if schema retrieval fails
   */
  public Schema getSchema(LdapServerConfig config) throws LDAPException {
    try {
      LDAPConnectionPool pool = getConnectionPool(config);
      
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
    } catch (GeneralSecurityException e) {
      throw new LDAPException(
          ResultCode.LOCAL_ERROR,
          "SSL/TLS error: " + e.getMessage()
      );
    }
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
   *
   * @param config server configuration
   * @return RootDSE object or null if not available
   * @throws LDAPException if retrieval fails
   * @throws GeneralSecurityException if SSL/TLS setup fails
   */
  public RootDSE getRootDSE(LdapServerConfig config) 
      throws LDAPException, GeneralSecurityException {
    LDAPConnectionPool pool = getConnectionPool(config);
    return pool.getRootDSE();
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

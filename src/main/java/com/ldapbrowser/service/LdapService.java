package com.ldapbrowser.service;

import com.ldapbrowser.model.BrowseResult;
import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.StartTLSPostConnectProcessor;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
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
}

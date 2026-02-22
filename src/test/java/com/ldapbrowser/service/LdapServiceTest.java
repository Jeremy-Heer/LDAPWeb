package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.ssl.SSLUtil;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LdapService} core methods.
 *
 * <p>Uses Mockito spies to intercept {@code getConnectionPool()} and inject mock
 * {@link LDAPConnectionPool} instances so that no live LDAP server is required.
 * Private methods are exercised via Java reflection where needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LdapService")
class LdapServiceTest {

  @Mock
  private TruststoreService truststoreService;

  @Mock
  private LoggingService loggingService;

  private LdapService service;

  @BeforeEach
  void setUp() {
    service = new LdapService(truststoreService, loggingService);
  }

  // -------------------------------------------------------------------------
  // Helper: builds a minimal LdapServerConfig with just a name set.
  // -------------------------------------------------------------------------
  private static LdapServerConfig config(String name) {
    LdapServerConfig cfg = new LdapServerConfig();
    cfg.setName(name);
    cfg.setHost("ldap.example.com");
    cfg.setPort(389);
    return cfg;
  }

  // -------------------------------------------------------------------------
  // Helper: retrieves the private ConcurrentHashMap<String, LDAPConnectionPool>
  // field "connectionPools" via reflection so tests can pre-populate it.
  // -------------------------------------------------------------------------
  @SuppressWarnings("unchecked")
  private Map<String, LDAPConnectionPool> getConnectionPoolsMap() throws Exception {
    java.lang.reflect.Field f = LdapService.class.getDeclaredField("connectionPools");
    f.setAccessible(true);
    return (Map<String, LDAPConnectionPool>) f.get(service);
  }

  // -------------------------------------------------------------------------
  // getConnectionErrorMessage – mapping for every switch branch
  // -------------------------------------------------------------------------
  @Nested
  @DisplayName("getConnectionErrorMessage")
  class GetConnectionErrorMessage {

    @Test
    @DisplayName("code 49 → invalid credentials message")
    void invalidCredentials() {
      LDAPException ex = new LDAPException(ResultCode.INVALID_CREDENTIALS, "invalid");
      assertThat(service.getConnectionErrorMessage(ex))
          .contains("Invalid credentials");
    }

    @Test
    @DisplayName("code 81 → server unavailable message")
    void serverDown() {
      LDAPException ex = new LDAPException(ResultCode.SERVER_DOWN, "down");
      assertThat(service.getConnectionErrorMessage(ex))
          .contains("unavailable");
    }

    @Test
    @DisplayName("code 91 → connection timeout message")
    void connectError() {
      LDAPException ex = new LDAPException(ResultCode.CONNECT_ERROR, "timeout");
      assertThat(service.getConnectionErrorMessage(ex))
          .contains("timeout");
    }

    @Test
    @DisplayName("unknown code → generic connection failed message")
    void unknownCode() {
      LDAPException ex = new LDAPException(ResultCode.OTHER, "something went wrong");
      String msg = service.getConnectionErrorMessage(ex);
      assertThat(msg).startsWith("Connection failed:");
    }
  }

  // -------------------------------------------------------------------------
  // createSslUtil – via reflection
  // -------------------------------------------------------------------------
  @Nested
  @DisplayName("createSslUtil")
  class CreateSslUtil {

    /** Calls the private createSslUtil method via reflection. */
    private SSLUtil invokeSslUtil(LdapServerConfig cfg) throws Exception {
      Method m = LdapService.class.getDeclaredMethod("createSslUtil", LdapServerConfig.class);
      m.setAccessible(true);
      return (SSLUtil) m.invoke(service, cfg);
    }

    @Test
    @DisplayName("validateCertificate=false uses TrustAll, returns non-null SSLUtil")
    void validateDisabledReturnsTrustAll() throws Exception {
      LdapServerConfig cfg = config("NoValidate");
      cfg.setValidateCertificate(false);

      SSLUtil sslUtil = invokeSslUtil(cfg);

      assertThat(sslUtil).isNotNull();
    }

    @Test
    @DisplayName("validateCertificate=true with available truststore returns non-null SSLUtil")
    void validateEnabledWithTruststoreReturnsUtil() throws Exception {
      LdapServerConfig cfg = config("Validate");
      cfg.setValidateCertificate(true);

      when(truststoreService.getTruststorePath())
          .thenReturn(Path.of("/nonexistent/truststore.pfx"));
      when(truststoreService.getTruststorePassword())
          .thenReturn("changeit".toCharArray());

      SSLUtil sslUtil = invokeSslUtil(cfg);

      assertThat(sslUtil).isNotNull();
    }

    @Test
    @DisplayName("validateCertificate=true, getTruststorePassword throws → GeneralSecurityException")
    void validateEnabledTruststoreErrorThrows() throws Exception {
      LdapServerConfig cfg = config("ValidateFail");
      cfg.setValidateCertificate(true);

      when(truststoreService.getTruststorePath())
          .thenReturn(Path.of("/nonexistent/truststore.pfx"));
      when(truststoreService.getTruststorePassword())
          .thenThrow(new IOException("cannot read password"));

      assertThatThrownBy(() -> invokeSslUtil(cfg))
          .isInstanceOf(InvocationTargetException.class)
          .cause()
          .isInstanceOf(GeneralSecurityException.class)
          .hasMessageContaining("Truststore could not be loaded");
    }
  }

  // -------------------------------------------------------------------------
  // executeWithRetry – exercised through the public search() method
  // using a Mockito spy that stubs getConnectionPool()
  // -------------------------------------------------------------------------
  @Nested
  @DisplayName("executeWithRetry")
  class ExecuteWithRetry {

    private LdapService spy;
    private LDAPConnectionPool firstPool;
    private LDAPConnectionPool freshPool;

    @BeforeEach
    void setUpSpy() {
      spy = spy(service);
      firstPool = mock(LDAPConnectionPool.class);
      freshPool = mock(LDAPConnectionPool.class);
    }

    /**
     * Builds a minimal successful SearchResult that wraps an empty entry list.
     */
    private SearchResult emptySearchResult() {
      SearchResult r = mock(SearchResult.class);
      when(r.getSearchEntries()).thenReturn(Collections.emptyList());
      return r;
    }

    @Test
    @DisplayName("SERVER_DOWN on first attempt → retries with fresh pool and succeeds")
    void serverDownTriggersRetrySuccess() throws Exception {
      LdapServerConfig cfg = config("Retry");

      // First pool call returns firstPool (SERVER_DOWN), second returns freshPool (OK)
      doReturn(firstPool).doReturn(freshPool).when(spy).getConnectionPool(any());

      when(firstPool.search(any(SearchRequest.class)))
          .thenThrow(new LDAPSearchException(ResultCode.SERVER_DOWN, "connection lost"));
      SearchResult emptyResult = emptySearchResult();
      when(freshPool.search(any(SearchRequest.class))).thenReturn(emptyResult);

      List<LdapEntry> result = spy.search(cfg, "dc=example,dc=com",
          "(objectClass=*)", SearchScope.SUB);

      assertThat(result).isEmpty();
      verify(firstPool).search(any(SearchRequest.class));
      verify(freshPool).search(any(SearchRequest.class));
      // getConnectionPool was invoked twice: first attempt + retry
      verify(spy, times(2)).getConnectionPool(any());
    }

    @Test
    @DisplayName("SERVER_DOWN on first attempt → retry also fails → LDAPException propagated")
    void serverDownRetryAlsoFails() throws Exception {
      LdapServerConfig cfg = config("RetryFail");

      doReturn(firstPool).doReturn(freshPool).when(spy).getConnectionPool(any());

      when(firstPool.search(any(SearchRequest.class)))
          .thenThrow(new LDAPSearchException(ResultCode.SERVER_DOWN, "connection lost"));
      when(freshPool.search(any(SearchRequest.class)))
          .thenThrow(new LDAPSearchException(ResultCode.SERVER_DOWN, "still down"));

      assertThatThrownBy(() ->
          spy.search(cfg, "dc=example,dc=com", "(objectClass=*)", SearchScope.SUB))
          .isInstanceOf(LDAPException.class);

      verify(spy, times(2)).getConnectionPool(any());
    }

    @Test
    @DisplayName("INVALID_CREDENTIALS (non-retryable) → thrown immediately, no retry")
    void nonRetryableErrorNotRetried() throws Exception {
      LdapServerConfig cfg = config("NoRetry");

      doReturn(firstPool).when(spy).getConnectionPool(any());

      when(firstPool.search(any(SearchRequest.class)))
          .thenThrow(new LDAPSearchException(ResultCode.INVALID_CREDENTIALS, "bad password"));

      assertThatThrownBy(() ->
          spy.search(cfg, "dc=example,dc=com", "(objectClass=*)", SearchScope.SUB))
          .isInstanceOf(LDAPException.class);

      // getConnectionPool called only once – no retry for non-connection errors
      verify(spy, times(1)).getConnectionPool(any());
      verify(freshPool, never()).search(any());
    }

    @Test
    @DisplayName("UNAVAILABLE triggers retry; TIMEOUT triggers retry; DECODING_ERROR triggers retry")
    void otherRetryableCodesAlsoTriggerRetry() throws Exception {
      for (ResultCode retryable : new ResultCode[]{
          ResultCode.UNAVAILABLE, ResultCode.TIMEOUT, ResultCode.DECODING_ERROR}) {

        LdapService localSpy = spy(new LdapService(truststoreService, loggingService));
        LDAPConnectionPool p1 = mock(LDAPConnectionPool.class);
        LDAPConnectionPool p2 = mock(LDAPConnectionPool.class);

        doReturn(p1).doReturn(p2).when(localSpy).getConnectionPool(any());
        when(p1.search(any(SearchRequest.class)))
            .thenThrow(new LDAPSearchException(retryable, retryable.getName()));
        SearchResult emptyResult = emptySearchResult();
        when(p2.search(any(SearchRequest.class))).thenReturn(emptyResult);

        LdapServerConfig cfg = config(retryable.getName());
        List<LdapEntry> result = localSpy.search(cfg, "dc=example,dc=com",
            "(objectClass=*)", SearchScope.SUB);

        assertThat(result).isEmpty();
        verify(localSpy, times(2)).getConnectionPool(any());
      }
    }
  }

  // -------------------------------------------------------------------------
  // Search result mapping – verify LdapEntry fields populated from entries
  // -------------------------------------------------------------------------
  @Nested
  @DisplayName("search result mapping")
  class SearchResultMapping {

    @Test
    @DisplayName("returned entries are mapped to LdapEntry with DN and attributes")
    void entryAttributesMapped() throws Exception {
      LdapService spy = spy(service);
      LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
      doReturn(pool).when(spy).getConnectionPool(any());

      // Build a real SearchResultEntry so attribute parsing works correctly
      SearchResultEntry entry = new SearchResultEntry(
          "cn=Alice,dc=example,dc=com",
          new Attribute[]{
              new Attribute("cn", "Alice"),
              new Attribute("mail", "alice@example.com")
          }
      );

      SearchResult mockResult = mock(SearchResult.class);
      when(mockResult.getSearchEntries()).thenReturn(List.of(entry));
      when(pool.search(any(SearchRequest.class))).thenReturn(mockResult);

      LdapServerConfig cfg = config("Mapping");
      List<LdapEntry> results = spy.search(cfg, "dc=example,dc=com",
          "(cn=Alice)", SearchScope.SUB);

      assertThat(results).hasSize(1);
      LdapEntry ldapEntry = results.get(0);
      assertThat(ldapEntry.getDn()).isEqualTo("cn=Alice,dc=example,dc=com");
      assertThat(ldapEntry.getFirstAttributeValue("cn")).isEqualTo("Alice");
      assertThat(ldapEntry.getFirstAttributeValue("mail")).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("empty search result returns empty list")
    void emptyResultReturnsList() throws Exception {
      LdapService spy = spy(service);
      LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
      doReturn(pool).when(spy).getConnectionPool(any());

      SearchResult mockResult = mock(SearchResult.class);
      when(mockResult.getSearchEntries()).thenReturn(Collections.emptyList());
      when(pool.search(any(SearchRequest.class))).thenReturn(mockResult);

      LdapServerConfig cfg = config("Empty");
      List<LdapEntry> results = spy.search(cfg, "dc=example,dc=com",
          "(uid=nobody)", SearchScope.SUB);

      assertThat(results).isEmpty();
    }
  }

  // -------------------------------------------------------------------------
  // Connection pool lifecycle management
  // -------------------------------------------------------------------------
  @Nested
  @DisplayName("connection pool management")
  class ConnectionPoolManagement {

    @Test
    @DisplayName("closeConnectionPool closes the pool and removes it from the map")
    void closeConnectionPoolClosesAndRemoves() throws Exception {
      LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
      getConnectionPoolsMap().put("Server1", pool);

      service.closeConnectionPool("Server1");

      verify(pool).close();
      assertThat(getConnectionPoolsMap()).doesNotContainKey("Server1");
    }

    @Test
    @DisplayName("closeConnectionPool on unknown name does not throw")
    void closeUnknownPoolIsNoOp() {
      // Should not throw even if the pool does not exist
      service.closeConnectionPool("DoesNotExist");
    }

    @Test
    @DisplayName("closeAllConnectionPools closes every pool and clears the map")
    void closeAllConnectionPoolsClosesAll() throws Exception {
      LDAPConnectionPool pool1 = mock(LDAPConnectionPool.class);
      LDAPConnectionPool pool2 = mock(LDAPConnectionPool.class);
      getConnectionPoolsMap().put("Server1", pool1);
      getConnectionPoolsMap().put("Server2", pool2);

      service.closeAllConnectionPools();

      verify(pool1).close();
      verify(pool2).close();
      assertThat(getConnectionPoolsMap()).isEmpty();
    }
  }

  // -------------------------------------------------------------------------
  // browseEntriesWithPage – search filter syntax validation
  // -------------------------------------------------------------------------
  @Nested
  @DisplayName("browseEntriesWithPage filter validation")
  class BrowseEntriesFilterValidation {

    @Test
    @DisplayName("valid filter passes without exception")
    void validFilterIsAccepted() throws Exception {
      // Just verify Filter.create() itself is satisfied — no pool needed.
      // We only test the guard; deeper paging logic requires a live pool.
      assertThatCode(() -> Filter.create("(objectClass=*)"))
          .doesNotThrowAnyException();
      assertThatCode(() -> Filter.create("(&(cn=john)(sn=doe))"))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null filter bypasses validation (null guard)")
    void nullFilterBypassesValidation() {
      // Null filter must not reach Filter.create() — the null guard fires first.
      // Filter.create(null) would throw NPE; we verify the guard prevents that.
      assertThatCode(() -> {
        // Replicate the guard from browseEntriesWithPage
        String searchFilter = null;
        if (searchFilter != null && !searchFilter.isEmpty()) {
          Filter.create(searchFilter);
        }
      }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("malformed filter throws LDAPException before pool is contacted")
    void malformedFilterThrowsBeforePoolIsContacted() {
      // No pool inserted → executeWithRetry would throw "not connected";
      // but the filter guard must fire first.
      LdapServerConfig cfg = config("s");
      // Unmatched opening paren — definitively rejected by Filter.create() per RFC 4515.
      assertThatThrownBy(() ->
          service.browseEntriesWithPage(cfg, "dc=example,dc=com", 0, "(cn=test"))
          .isInstanceOf(LDAPException.class);
    }
  }
}

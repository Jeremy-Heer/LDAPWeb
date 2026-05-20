package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ldapbrowser.model.BrowseResult;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LdapBrowseSearchExecutor")
class LdapBrowseSearchExecutorTest {

  @Test
  @DisplayName("executePageSearch maps result and updates paging state")
  void executePageSearchMapsAndUpdatesPagingState() throws Exception {
    LdapBrowseSearchExecutor executor = new LdapBrowseSearchExecutor();
    LdapBrowsePageResultMapper mapper = new LdapBrowsePageResultMapper();
    LdapPagingStateManager pagingStateManager = new LdapPagingStateManager();

    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
    SearchResult result = mock(SearchResult.class);

    SearchResultEntry entry = new SearchResultEntry(
        "cn=alpha,dc=example,dc=com",
        new Attribute[] {new Attribute("cn", "alpha")});

    Control pagedControl = new SimplePagedResultsControl(
        100,
        new ASN1OctetString(new byte[] {4, 5}),
        false);

    when(result.getSearchEntries()).thenReturn(List.of(entry));
    when(result.getResponseControls()).thenReturn(new Control[] {pagedControl});
    when(pool.search(any(SearchRequest.class))).thenReturn(result);

    SearchRequest request = new SearchRequest("dc=example,dc=com", com.unboundid.ldap.sdk.SearchScope.ONE,
        Filter.create("(objectClass=*)"));

    BrowseResult browseResult = executor.executePageSearch(
        pool,
        request,
        "serverA",
        "serverA:dc=example,dc=com",
        1,
        100,
        "dc=example,dc=com",
        ldapEntry -> false,
        mapper,
        pagingStateManager);

    assertThat(browseResult.getEntries()).hasSize(1);
    assertThat(browseResult.hasNextPage()).isTrue();
    assertThat(browseResult.hasPrevPage()).isTrue();
    assertThat(browseResult.getCurrentPage()).isEqualTo(1);

    LdapPagingStateManager.PagingState nextState =
        pagingStateManager.preparePageRequest("serverA:dc=example,dc=com", 2);
    assertThat(nextState.requiresIteration()).isFalse();
    assertThat(nextState.cookie()).containsExactly(4, 5);
  }

  @Test
  @DisplayName("executePageSearch returns partial browse result for size-limit exceeded")
  void executePageSearchHandlesSizeLimitExceeded() throws Exception {
    LdapBrowseSearchExecutor executor = new LdapBrowseSearchExecutor();
    LdapBrowsePageResultMapper mapper = new LdapBrowsePageResultMapper();
    LdapPagingStateManager pagingStateManager = new LdapPagingStateManager();

    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);

    SearchResultEntry entry = new SearchResultEntry(
        "cn=alpha,dc=example,dc=com",
        new Attribute[] {new Attribute("cn", "alpha")});

    LDAPSearchException sizeLimit = mock(LDAPSearchException.class);
    when(sizeLimit.getResultCode()).thenReturn(ResultCode.SIZE_LIMIT_EXCEEDED);
    when(sizeLimit.getSearchEntries()).thenReturn(List.of(entry));
    when(pool.search(any(SearchRequest.class))).thenThrow(sizeLimit);

    SearchRequest request = new SearchRequest("dc=example,dc=com", com.unboundid.ldap.sdk.SearchScope.ONE,
        Filter.create("(objectClass=*)"));

    BrowseResult browseResult = executor.executePageSearch(
        pool,
        request,
        "serverA",
        "serverA:dc=example,dc=com",
        0,
        100,
        "dc=example,dc=com",
        ldapEntry -> false,
        mapper,
        pagingStateManager);

    assertThat(browseResult.getEntries()).hasSize(1);
    assertThat(browseResult.hasNextPage()).isTrue();
    assertThat(browseResult.hasPrevPage()).isFalse();
  }

  @Test
  @DisplayName("executePageSearch rethrows non-size-limit LDAPSearchException")
  void executePageSearchRethrowsOtherLdapSearchException() throws Exception {
    LdapBrowseSearchExecutor executor = new LdapBrowseSearchExecutor();
    LdapBrowsePageResultMapper mapper = new LdapBrowsePageResultMapper();
    LdapPagingStateManager pagingStateManager = new LdapPagingStateManager();

    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);

    LDAPSearchException otherError = mock(LDAPSearchException.class);
    when(otherError.getResultCode()).thenReturn(ResultCode.TIMEOUT);
    when(pool.search(any(SearchRequest.class))).thenThrow(otherError);

    SearchRequest request = new SearchRequest("dc=example,dc=com", com.unboundid.ldap.sdk.SearchScope.ONE,
        Filter.create("(objectClass=*)"));

    assertThatThrownBy(() -> executor.executePageSearch(
        pool,
        request,
        "serverA",
        "serverA:dc=example,dc=com",
        0,
        100,
        "dc=example,dc=com",
        ldapEntry -> false,
        mapper,
        pagingStateManager)).isSameAs(otherError);
  }
}

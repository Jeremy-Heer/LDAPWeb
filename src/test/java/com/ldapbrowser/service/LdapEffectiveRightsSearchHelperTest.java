package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("LdapEffectiveRightsSearchHelper")
class LdapEffectiveRightsSearchHelperTest {

  private final LdapEffectiveRightsSearchHelper helper = new LdapEffectiveRightsSearchHelper();

  @Test
  @DisplayName("adds aclRights and preserves wildcard attributes")
  void preservesWildcardAttributes() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
    SearchResult result = mock(SearchResult.class);
    when(result.getResultCode()).thenReturn(ResultCode.SUCCESS);
    when(result.getEntryCount()).thenReturn(1);
    when(result.getSearchEntries()).thenReturn(List.of(entry("cn=test")));
    when(pool.search(any(SearchRequest.class))).thenReturn(result);

    LdapService.SearchResultWithEffectiveRights searchResult = helper.searchEffectiveRights(
        pool,
        "dc=example,dc=com",
        SearchScope.SUB,
        "(objectClass=*)",
        "*",
        "dn:uid=user,dc=example,dc=com",
        0
    );

    ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(pool).search(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getAttributes()).containsExactly("*", "aclRights");
    assertThat(searchResult.getEntries()).hasSize(1);
    assertThat(searchResult.isSizeLimitExceeded()).isFalse();
  }

  @Test
  @DisplayName("flags size limit when server returns partial results")
  void flagsSizeLimitWhenPartialResultsReturned() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
    SearchResult partialResult = mock(SearchResult.class);
    when(partialResult.getSearchEntries()).thenReturn(List.of(entry("cn=partial")));

    LDAPSearchException exception = mock(LDAPSearchException.class);
    when(exception.getResultCode()).thenReturn(ResultCode.SIZE_LIMIT_EXCEEDED);
    when(exception.getSearchResult()).thenReturn(partialResult);
    when(pool.search(any(SearchRequest.class))).thenThrow(exception);

    LdapService.SearchResultWithEffectiveRights searchResult = helper.searchEffectiveRights(
        pool,
        "dc=example,dc=com",
        SearchScope.SUB,
        "(objectClass=*)",
        "cn, sn",
        "dn:uid=user,dc=example,dc=com",
        10
    );

    assertThat(searchResult.getEntries()).hasSize(1);
    assertThat(searchResult.isSizeLimitExceeded()).isTrue();
  }

  @Test
  @DisplayName("converts unsupported control errors to a friendly LDAPException")
  void convertsUnsupportedControlError() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
    LDAPSearchException exception = mock(LDAPSearchException.class);
    when(exception.getResultCode()).thenReturn(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
    when(pool.search(any(SearchRequest.class))).thenThrow(exception);

    assertThatThrownBy(() -> helper.searchEffectiveRights(
        pool,
        "dc=example,dc=com",
        SearchScope.SUB,
        "(objectClass=*)",
        "cn",
        "dn:uid=user,dc=example,dc=com",
        0
    ))
        .isInstanceOf(LDAPException.class)
        .hasMessageContaining("GetEffectiveRightsRequestControl");
  }

  private static SearchResultEntry entry(String dn) {
    return new SearchResultEntry(dn, new Attribute[] {new Attribute("cn", "value")});
  }
}
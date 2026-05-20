package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ldapbrowser.model.LdapEntry;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("LdapBrowsePageResultMapper")
class LdapBrowsePageResultMapperTest {

  @Test
  @DisplayName("mapEntries maps attributes, marks children, and sorts by display name")
  void mapEntriesMapsAndSorts() {
    LdapBrowsePageResultMapper mapper = new LdapBrowsePageResultMapper();

    SearchResultEntry zEntry = new SearchResultEntry(
        "cn=zeta,dc=example,dc=com",
        new Attribute[] {
            new Attribute("cn", "zeta"),
            new Attribute("mail", "zeta@example.com")
        });

    SearchResultEntry aEntry = new SearchResultEntry(
        "cn=Alpha,dc=example,dc=com",
        new Attribute[] {
            new Attribute("cn", "Alpha"),
            new Attribute("mail", "alpha@example.com")
        });

    List<LdapEntry> entries = mapper.mapEntries(
        "ServerA",
        List.of(zEntry, aEntry),
        entry -> entry.getDn().startsWith("cn=Alpha"));

    assertThat(entries).hasSize(2);
    assertThat(entries.get(0).getDisplayName()).isEqualTo("cn=Alpha");
    assertThat(entries.get(1).getDisplayName()).isEqualTo("cn=zeta");

    assertThat(entries.get(0).getServerName()).isEqualTo("ServerA");
    assertThat(entries.get(0).hasChildren()).isTrue();
    assertThat(entries.get(0).getFirstAttributeValue("mail")).isEqualTo("alpha@example.com");
    assertThat(entries.get(1).hasChildren()).isFalse();
  }

  @Test
  @DisplayName("updatePagingState stores next-page cookie when paged control has cookie")
  void updatePagingStateStoresCookie() {
    LdapBrowsePageResultMapper mapper = new LdapBrowsePageResultMapper();
    LdapPagingStateManager pagingStateManager = new LdapPagingStateManager();

    SearchResult searchResult = Mockito.mock(SearchResult.class);
    Control pagedControl = new SimplePagedResultsControl(
        100,
        new ASN1OctetString(new byte[] {7, 8, 9}),
        false);
    Mockito.when(searchResult.getResponseControls()).thenReturn(new Control[] {pagedControl});

    boolean hasMorePages = mapper.updatePagingState(searchResult, "serverA:dc=example,dc=com", 0,
        pagingStateManager);

    assertThat(hasMorePages).isTrue();
    LdapPagingStateManager.PagingState state =
        pagingStateManager.preparePageRequest("serverA:dc=example,dc=com", 1);
    assertThat(state.requiresIteration()).isFalse();
    assertThat(state.cookie()).containsExactly(7, 8, 9);
  }

  @Test
  @DisplayName("updatePagingState clears context when cookie is empty")
  void updatePagingStateClearsContextOnEmptyCookie() {
    LdapBrowsePageResultMapper mapper = new LdapBrowsePageResultMapper();
    LdapPagingStateManager pagingStateManager = new LdapPagingStateManager();
    String searchKey = "serverA:dc=example,dc=com";

    pagingStateManager.storeNextPage(searchKey, 0, new byte[] {1, 2, 3});

    SearchResult searchResult = Mockito.mock(SearchResult.class);
    Control pagedControl = new SimplePagedResultsControl(100, new ASN1OctetString(new byte[0]), false);
    Mockito.when(searchResult.getResponseControls()).thenReturn(new Control[] {pagedControl});

    boolean hasMorePages = mapper.updatePagingState(searchResult, searchKey, 1, pagingStateManager);

    assertThat(hasMorePages).isFalse();
    assertThat(pagingStateManager.preparePageRequest(searchKey, 1).requiresIteration()).isTrue();
  }

  @Test
  @DisplayName("updatePagingState returns false when no paged control is present")
  void updatePagingStateReturnsFalseWithoutPagedControl() {
    LdapBrowsePageResultMapper mapper = new LdapBrowsePageResultMapper();
    LdapPagingStateManager pagingStateManager = new LdapPagingStateManager();

    SearchResult searchResult = Mockito.mock(SearchResult.class);
    Mockito.when(searchResult.getResponseControls()).thenReturn(new Control[0]);

    boolean hasMorePages = mapper.updatePagingState(searchResult, "serverA:dc=example,dc=com", 0,
        pagingStateManager);

    assertThat(hasMorePages).isFalse();
  }
}

package com.ldapbrowser.service;

import com.ldapbrowser.model.LdapEntry;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Maps LDAP paged-search results into domain entries and updates paging state.
 */
class LdapBrowsePageResultMapper {

  List<LdapEntry> mapEntries(
      String serverName,
      List<SearchResultEntry> searchEntries,
      Predicate<LdapEntry> hasChildrenPredicate) {
    List<LdapEntry> entries = new ArrayList<>();
    for (SearchResultEntry entry : searchEntries) {
      LdapEntry ldapEntry = new LdapEntry(entry.getDN(), serverName);
      for (Attribute attr : entry.getAttributes()) {
        for (String value : attr.getValues()) {
          ldapEntry.addAttribute(attr.getName(), value);
        }
      }
      ldapEntry.setHasChildren(hasChildrenPredicate.test(ldapEntry));
      entries.add(ldapEntry);
    }
    entries.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
    return entries;
  }

  boolean updatePagingState(
      SearchResult searchResult,
      String searchKey,
      int pageNumber,
      LdapPagingStateManager pagingStateManager) {
    SimplePagedResultsControl responseControl = null;
    for (Control control : searchResult.getResponseControls()) {
      if (control instanceof SimplePagedResultsControl) {
        responseControl = (SimplePagedResultsControl) control;
        break;
      }
    }

    if (responseControl == null) {
      return false;
    }

    ASN1OctetString cookieOctetString = responseControl.getCookie();
    if (cookieOctetString != null && cookieOctetString.getValueLength() > 0) {
      pagingStateManager.storeNextPage(searchKey, pageNumber, cookieOctetString.getValue());
      return true;
    }

    pagingStateManager.clearSearchContext(searchKey);
    return false;
  }
}

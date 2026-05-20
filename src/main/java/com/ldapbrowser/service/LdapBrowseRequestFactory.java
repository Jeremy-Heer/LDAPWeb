package com.ldapbrowser.service;

import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;

/**
 * Creates LDAP browse requests with paged-results controls.
 */
class LdapBrowseRequestFactory {

  private static final String[] BROWSE_ATTRIBUTES = {"objectClass", "cn", "ou", "dc"};

  SearchRequest createPagedBrowseRequest(String baseDn, Filter filter, int pageSize, byte[] cookie) {
    SearchRequest searchRequest =
        new SearchRequest(baseDn, SearchScope.ONE, filter, BROWSE_ATTRIBUTES);
    searchRequest.addControl(createPagedControl(pageSize, cookie));
    return searchRequest;
  }

  private SimplePagedResultsControl createPagedControl(int pageSize, byte[] cookie) {
    if (cookie != null) {
      return new SimplePagedResultsControl(pageSize, new ASN1OctetString(cookie), false);
    }
    return new SimplePagedResultsControl(pageSize, false);
  }
}

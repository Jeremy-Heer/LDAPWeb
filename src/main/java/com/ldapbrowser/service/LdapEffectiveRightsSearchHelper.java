package com.ldapbrowser.service;

import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.unboundidds.controls.GetEffectiveRightsRequestControl;
import java.util.ArrayList;

/**
 * Helper for search requests that request effective rights information.
 */
final class LdapEffectiveRightsSearchHelper {

  LdapService.SearchResultWithEffectiveRights searchEffectiveRights(
      LDAPConnectionPool pool,
      String searchBase,
      SearchScope scope,
      String filter,
      String attributes,
      String effectiveRightsFor,
      int sizeLimit
  ) throws LDAPException {
    try {
      String[] attributeArray = buildAttributeArray(attributes);
      SearchRequest searchRequest = new SearchRequest(
          searchBase,
          scope,
          Filter.create(filter),
          attributeArray
      );

      searchRequest.setSizeLimit(sizeLimit);
      searchRequest.addControl(new GetEffectiveRightsRequestControl(effectiveRightsFor));

      SearchResult searchResult = pool.search(searchRequest);
      boolean sizeLimitExceeded = (searchResult.getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED)
          || (sizeLimit > 0 && searchResult.getEntryCount() >= sizeLimit);

      return new LdapService.SearchResultWithEffectiveRights(
          searchResult.getSearchEntries(),
          sizeLimitExceeded
      );
    } catch (LDAPSearchException e) {
      if (e.getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED) {
        SearchResult partialResult = e.getSearchResult();
        if (partialResult != null) {
          return new LdapService.SearchResultWithEffectiveRights(
              partialResult.getSearchEntries(),
              true
          );
        }
        return new LdapService.SearchResultWithEffectiveRights(new ArrayList<>(), true);
      }
      if (e.getResultCode() == ResultCode.UNAVAILABLE_CRITICAL_EXTENSION) {
        throw new LDAPException(
            ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
            "Server does not support GetEffectiveRightsRequestControl",
            e
        );
      }
      throw e;
    }
  }

  private String[] buildAttributeArray(String attributes) {
    if ("*".equals(attributes.trim())) {
      return new String[] {"*", "aclRights"};
    }

    ArrayList<String> attrList = new ArrayList<>();
    for (String attr : attributes.split(",")) {
      attrList.add(attr.trim());
    }
    attrList.add("aclRights");
    return attrList.toArray(new String[0]);
  }
}
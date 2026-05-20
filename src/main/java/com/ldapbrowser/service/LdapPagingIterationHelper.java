package com.ldapbrowser.service;

import com.ldapbrowser.model.BrowseResult;
import com.unboundid.ldap.sdk.LDAPException;
import java.util.ArrayList;

/**
 * Iterates paged LDAP results until a target page is reached.
 */
class LdapPagingIterationHelper {

  @FunctionalInterface
  interface PageFetcher {
    BrowseResult fetch(int pageNumber) throws LDAPException;
  }

  BrowseResult fetchTargetPage(int targetPage, int pageSize, PageFetcher pageFetcher)
      throws LDAPException {
    boolean hasMorePages = true;
    BrowseResult lastResult = null;

    for (int currentPage = 0; currentPage <= targetPage && hasMorePages; currentPage++) {
      lastResult = pageFetcher.fetch(currentPage);
      if (currentPage == targetPage) {
        return lastResult;
      }
      hasMorePages = lastResult.hasNextPage();
    }

    return new BrowseResult(new ArrayList<>(), false, 0, targetPage, pageSize, false, targetPage > 0);
  }
}

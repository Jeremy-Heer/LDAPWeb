package com.ldapbrowser.service;

import com.ldapbrowser.model.BrowseResult;
import com.ldapbrowser.model.LdapEntry;
import java.util.List;

/**
 * Creates {@link BrowseResult} instances for browse pagination scenarios.
 */
class LdapBrowseResultFactory {

  BrowseResult createPageResult(
      List<LdapEntry> entries,
      int pageNumber,
      int pageSize,
      boolean hasNextPage) {
    return new BrowseResult(
        entries,
        hasNextPage,
        entries.size(),
        pageNumber,
        pageSize,
        hasNextPage,
        pageNumber > 0);
  }

  BrowseResult createSizeLimitResult(List<LdapEntry> entries, int pageNumber, int pageSize) {
    return new BrowseResult(
        entries,
        true,
        entries.size(),
        pageNumber,
        pageSize,
        true,
        pageNumber > 0);
  }
}

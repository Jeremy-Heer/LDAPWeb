package com.ldapbrowser.service;

import com.ldapbrowser.model.BrowseResult;
import com.ldapbrowser.model.LdapEntry;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes paged LDAP browse searches and maps results to browse DTOs.
 */
class LdapBrowseSearchExecutor {

  private static final Logger logger = LoggerFactory.getLogger(LdapBrowseSearchExecutor.class);
  private final LdapBrowseResultFactory browseResultFactory = new LdapBrowseResultFactory();

  BrowseResult executePageSearch(
      LDAPConnectionPool pool,
      SearchRequest searchRequest,
      String serverName,
      String searchKey,
      int pageNumber,
      int pageSize,
      String baseDn,
      Predicate<LdapEntry> hasChildrenPredicate,
      LdapBrowsePageResultMapper browsePageResultMapper,
      LdapPagingStateManager pagingStateManager)
      throws LDAPException {
    try {
      SearchResult searchResult = pool.search(searchRequest);
      List<LdapEntry> entries = browsePageResultMapper.mapEntries(
          serverName,
          searchResult.getSearchEntries(),
          hasChildrenPredicate);
      boolean hasNextPage = browsePageResultMapper.updatePagingState(
          searchResult, searchKey, pageNumber, pagingStateManager);

      return browseResultFactory.createPageResult(entries, pageNumber, pageSize, hasNextPage);
    } catch (LDAPSearchException e) {
      if (e.getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED) {
        List<LdapEntry> entries = browsePageResultMapper.mapEntries(
            serverName,
            e.getSearchEntries(),
            hasChildrenPredicate);

        logger.warn("Size limit exceeded for {}, returning {} partial results",
            baseDn, entries.size());

        return browseResultFactory.createSizeLimitResult(entries, pageNumber, pageSize);
      }
      throw e;
    }
  }
}

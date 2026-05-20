package com.ldapbrowser.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages LDAP paged-search state per server/base DN context.
 */
class LdapPagingStateManager {

  private final Map<String, byte[]> pagingCookies = new ConcurrentHashMap<>();
  private final Map<String, Integer> currentPages = new ConcurrentHashMap<>();

  PagingState preparePageRequest(String searchKey, int pageNumber) {
    Integer storedPage = currentPages.get(searchKey);
    byte[] cookie = pagingCookies.get(searchKey);

    if (pageNumber == 0) {
      pagingCookies.remove(searchKey);
      currentPages.put(searchKey, 0);
      return PagingState.sequential(null);
    }

    if (storedPage == null || storedPage != pageNumber - 1) {
      return PagingState.iterationRequired();
    }

    return PagingState.sequential(cookie);
  }

  void storeNextPage(String searchKey, int pageNumber, byte[] nextCookie) {
    pagingCookies.put(searchKey, nextCookie);
    currentPages.put(searchKey, pageNumber);
  }

  void clearSearchContext(String searchKey) {
    pagingCookies.remove(searchKey);
    currentPages.remove(searchKey);
  }

  void clearServer(String serverId) {
    pagingCookies.entrySet().removeIf(entry -> entry.getKey().startsWith(serverId + ":"));
    currentPages.entrySet().removeIf(entry -> entry.getKey().startsWith(serverId + ":"));
  }

  static String buildSearchKey(String serverId, String baseDn) {
    return serverId + ":" + baseDn;
  }

  static final class PagingState {
    private final byte[] cookie;
    private final boolean requiresIteration;

    private PagingState(byte[] cookie, boolean requiresIteration) {
      this.cookie = cookie;
      this.requiresIteration = requiresIteration;
    }

    static PagingState sequential(byte[] cookie) {
      return new PagingState(cookie, false);
    }

    static PagingState iterationRequired() {
      return new PagingState(null, true);
    }

    byte[] cookie() {
      return cookie;
    }

    boolean requiresIteration() {
      return requiresIteration;
    }
  }
}

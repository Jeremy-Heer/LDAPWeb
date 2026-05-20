package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LdapPagingStateManager")
class LdapPagingStateManagerTest {

  @Test
  @DisplayName("buildSearchKey combines server and base DN")
  void buildSearchKeyCombinesServerAndBaseDn() {
    String key = LdapPagingStateManager.buildSearchKey("serverA", "dc=example,dc=com");

    assertThat(key).isEqualTo("serverA:dc=example,dc=com");
  }

  @Test
  @DisplayName("first page request resets state and uses no cookie")
  void firstPageResetsState() {
    LdapPagingStateManager manager = new LdapPagingStateManager();
    String searchKey = LdapPagingStateManager.buildSearchKey("serverA", "dc=example,dc=com");

    manager.storeNextPage(searchKey, 2, new byte[] {1, 2, 3});

    LdapPagingStateManager.PagingState state = manager.preparePageRequest(searchKey, 0);

    assertThat(state.requiresIteration()).isFalse();
    assertThat(state.cookie()).isNull();

    LdapPagingStateManager.PagingState pageOneState = manager.preparePageRequest(searchKey, 1);
    assertThat(pageOneState.requiresIteration()).isFalse();
    assertThat(pageOneState.cookie()).isNull();
  }

  @Test
  @DisplayName("sequential request uses stored cookie")
  void sequentialRequestUsesStoredCookie() {
    LdapPagingStateManager manager = new LdapPagingStateManager();
    String searchKey = LdapPagingStateManager.buildSearchKey("serverA", "dc=example,dc=com");
    byte[] nextCookie = new byte[] {9, 8, 7};

    manager.storeNextPage(searchKey, 0, nextCookie);

    LdapPagingStateManager.PagingState state = manager.preparePageRequest(searchKey, 1);

    assertThat(state.requiresIteration()).isFalse();
    assertThat(state.cookie()).containsExactly(9, 8, 7);
  }

  @Test
  @DisplayName("non-sequential request requires iteration")
  void nonSequentialRequestRequiresIteration() {
    LdapPagingStateManager manager = new LdapPagingStateManager();
    String searchKey = LdapPagingStateManager.buildSearchKey("serverA", "dc=example,dc=com");

    manager.storeNextPage(searchKey, 0, new byte[] {1});

    LdapPagingStateManager.PagingState state = manager.preparePageRequest(searchKey, 3);

    assertThat(state.requiresIteration()).isTrue();
    assertThat(state.cookie()).isNull();
  }

  @Test
  @DisplayName("clearSearchContext removes cookie and page state")
  void clearSearchContextRemovesState() {
    LdapPagingStateManager manager = new LdapPagingStateManager();
    String searchKey = LdapPagingStateManager.buildSearchKey("serverA", "dc=example,dc=com");

    manager.storeNextPage(searchKey, 0, new byte[] {5});
    manager.clearSearchContext(searchKey);

    LdapPagingStateManager.PagingState state = manager.preparePageRequest(searchKey, 1);
    assertThat(state.requiresIteration()).isTrue();
  }

  @Test
  @DisplayName("clearServer removes all contexts for server")
  void clearServerRemovesAllServerContexts() {
    LdapPagingStateManager manager = new LdapPagingStateManager();
    String serverAKey1 = LdapPagingStateManager.buildSearchKey("serverA", "dc=example,dc=com");
    String serverAKey2 = LdapPagingStateManager.buildSearchKey("serverA", "ou=people,dc=example,dc=com");
    String serverBKey = LdapPagingStateManager.buildSearchKey("serverB", "dc=example,dc=com");

    manager.storeNextPage(serverAKey1, 0, new byte[] {1});
    manager.storeNextPage(serverAKey2, 0, new byte[] {2});
    manager.storeNextPage(serverBKey, 0, new byte[] {3});

    manager.clearServer("serverA");

    assertThat(manager.preparePageRequest(serverAKey1, 1).requiresIteration()).isTrue();
    assertThat(manager.preparePageRequest(serverAKey2, 1).requiresIteration()).isTrue();
    assertThat(manager.preparePageRequest(serverBKey, 1).requiresIteration()).isFalse();
  }
}

package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ldapbrowser.model.BrowseResult;
import com.ldapbrowser.model.LdapEntry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LdapPagingIterationHelper")
class LdapPagingIterationHelperTest {

  @Test
  @DisplayName("fetchTargetPage returns target page after sequential fetches")
  void fetchTargetPageReturnsTargetResult() throws Exception {
    LdapPagingIterationHelper helper = new LdapPagingIterationHelper();

    BrowseResult result = helper.fetchTargetPage(
        2,
        100,
        pageNumber -> new BrowseResult(
            Collections.<LdapEntry>emptyList(),
            true,
            0,
            pageNumber,
            100,
            pageNumber < 3,
            pageNumber > 0));

    assertThat(result.getCurrentPage()).isEqualTo(2);
    assertThat(result.getPageSize()).isEqualTo(100);
    assertThat(result.hasPrevPage()).isTrue();
  }

  @Test
  @DisplayName("fetchTargetPage returns empty fallback when intermediate page has no next page")
  void fetchTargetPageReturnsFallbackWhenTargetNotReachable() throws Exception {
    LdapPagingIterationHelper helper = new LdapPagingIterationHelper();
    AtomicInteger calls = new AtomicInteger();

    BrowseResult result = helper.fetchTargetPage(
        3,
        50,
        pageNumber -> {
          calls.incrementAndGet();
          boolean hasNext = pageNumber == 0;
          return new BrowseResult(
              Collections.<LdapEntry>emptyList(),
              hasNext,
              0,
              pageNumber,
              50,
              hasNext,
              pageNumber > 0);
        });

    assertThat(calls.get()).isEqualTo(2);
    assertThat(result.getEntries()).isEmpty();
    assertThat(result.getCurrentPage()).isEqualTo(3);
    assertThat(result.getPageSize()).isEqualTo(50);
    assertThat(result.hasNextPage()).isFalse();
    assertThat(result.hasPrevPage()).isTrue();
  }

  @Test
  @DisplayName("fetchTargetPage propagates LDAPException from page fetcher")
  void fetchTargetPagePropagatesLdapException() {
    LdapPagingIterationHelper helper = new LdapPagingIterationHelper();

    assertThatThrownBy(() -> helper.fetchTargetPage(
        1,
        100,
        pageNumber -> {
          throw new LDAPException(ResultCode.SERVER_DOWN, "down");
        }))
            .isInstanceOf(LDAPException.class)
            .hasMessageContaining("down");
  }
}
